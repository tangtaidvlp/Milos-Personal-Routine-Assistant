package com.tom.payment.routinemanager.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.tom.payment.routinemanager.model.DailyRoutine;
import com.tom.payment.routinemanager.model.DailyTask;
import com.tom.payment.routinemanager.repository.DailyRoutineRepository;
import com.tom.payment.routinemanager.repository.UserRepository;
import com.tom.payment.routinemanager.support.BaseComponentTest;
import com.tom.payment.routinemanager.support.TestUser;

/**
 * Flow: a user records what really happened on a day next to the plan, gets Buddy's
 * note, and chats about the day (F007). Today is Wednesday 2026-11-18 (test clock).
 * Plan-vs-reality grouping rules are in unit/ReflectionComparisonServiceTest.
 */
class ReflectionComponentTest extends BaseComponentTest {

    @Autowired private UserRepository userRepository;
    @Autowired private DailyRoutineRepository dailyRoutineRepository;
    @Autowired private TransactionTemplate tx;

    // ---------- US1: copy once, then independent ----------

    @Test
    void firstReflectionCopiesThePlanOnceAndIsIdempotent() throws Exception {
        TestUser user = newUser("reflect");
        LocalDate today = clock.today();
        JsonNode plan = seedPlan(user, today, task("Gym", "07:00", 60, "green"), task("Work", "09:00", 240, null));

        JsonNode view = ok(as(user, post(base(user, today))));
        assertTrue(view.get("exists").asBoolean());
        assertEquals(2, view.get("tasks").size());
        JsonNode gym = view.get("tasks").get(0);
        assertEquals("Gym", gym.get("name").asText());
        assertEquals("07:00", gym.get("startTime").asText());
        assertEquals("08:00", gym.get("endTime").asText());
        assertEquals(60, gym.get("durationMinutes").asInt());
        assertEquals("green", gym.get("color").asText());
        assertEquals(plan.get(0).get("id").asText(), gym.get("plannedTaskId").asText());
        assertEquals(2, view.at("/comparison/counts/asPlanned").asInt());

        // A plan change after creation is not copied again.
        addPlanTasks(user, planId(user, today), task("Read", "21:00", 30, null));
        JsonNode again = ok(as(user, post(base(user, today))));
        assertEquals(view.get("id").asText(), again.get("id").asText());
        assertEquals(2, again.get("tasks").size());
        assertEquals(1, again.at("/comparison/counts/skipped").asInt());
    }

    @Test
    void readingNeverCreatesAReflection() throws Exception {
        TestUser user = newUser("reflect");
        LocalDate today = clock.today();
        seedPlan(user, today, task("Gym", "07:00", 60, null));
        JsonNode view = ok(as(user, get(base(user, today))));
        assertFalse(view.get("exists").asBoolean());
        assertEquals(0, view.get("tasks").size());
        assertEquals(1, view.at("/comparison/counts/skipped").asInt());
        assertFalse(ok(as(user, get(base(user, today)))).get("exists").asBoolean());
    }

    @Test
    void reflectionEditsNeverTouchThePlanAndViceVersa() throws Exception {
        TestUser user = newUser("reflect");
        LocalDate today = clock.today();
        seedPlan(user, today, task("Gym", "07:00", 60, null), task("Work", "09:00", 240, null));
        JsonNode view = ok(as(user, post(base(user, today))));
        String gymId = view.get("tasks").get(0).get("id").asText();
        String workId = view.get("tasks").get(1).get("id").asText();

        ok(as(user, put(base(user, today) + "/tasks"), List.of(Map.of(
                "id", gymId, "name", "Short gym", "startTime", "07:30", "durationMinutes", 30))));
        ok(as(user, delete(base(user, today) + "/tasks").param("taskIds", workId)));
        JsonNode added = ok(as(user, post(base(user, today) + "/tasks"), List.of(Map.of(
                "name", "Coffee with Sam", "startTime", "16:00", "durationMinutes", 45))));

        DailyRoutine plan = planFor(user, today);
        assertEquals(2, plan.getTasks().size());
        DailyTask planGym = plan.getTasks().stream().filter(t -> t.getName().equals("Gym")).findFirst().orElseThrow();
        assertEquals(LocalTime.of(7, 0), planGym.getStartTime());
        assertEquals(60, planGym.getDurationMinutes());

        JsonNode counts = added.at("/comparison/counts");
        assertEquals(0, counts.get("asPlanned").asInt());
        assertEquals(1, counts.get("changed").asInt()); // renamed + shortened, still matched
        assertEquals(1, counts.get("skipped").asInt());
        assertEquals(1, counts.get("unplanned").asInt());
        assertEquals(300, added.at("/comparison/plannedMinutes").asInt());
        assertEquals(75, added.at("/comparison/actualMinutes").asInt());
        JsonNode coffee = added.get("tasks").get(1);
        assertTrue(coffee.get("plannedTaskId").isNull());
        assertNotNull(coffee.get("color").asText(null)); // F005 auto-assigned

        // A plan edit afterwards leaves the reflection unchanged.
        mvc.perform(as(user, put("/api/routine-manager/daily-routine/tasks"), List.of(Map.of(
                        "id", planGym.getId().toString(), "name", "Gym", "startTime", "06:00", "durationMinutes", 90))))
                .andExpect(status().isOk());
        JsonNode after = ok(as(user, get(base(user, today))));
        assertEquals("07:30", after.get("tasks").get(0).get("startTime").asText());
        assertEquals(30, after.get("tasks").get(0).get("durationMinutes").asInt());
    }

    @Test
    void overlappingRealityIsKeptAsEntered() throws Exception {
        TestUser user = newUser("reflect");
        LocalDate today = clock.today();
        ok(as(user, post(base(user, today))));
        JsonNode view = ok(as(user, post(base(user, today) + "/tasks"), List.of(
                Map.of("name", "Call mum", "startTime", "10:00", "durationMinutes", 60),
                Map.of("name", "Laundry", "startTime", "10:30", "durationMinutes", 30))));
        assertEquals("10:00", view.get("tasks").get(0).get("startTime").asText());
        assertEquals("10:30", view.get("tasks").get(1).get("startTime").asText());
    }

    @Test
    void futureDatesAreRefused() throws Exception {
        TestUser user = newUser("reflect");
        LocalDate tomorrow = clock.today().plusDays(1);
        mvc.perform(as(user, post(base(user, tomorrow)))).andExpect(error(HttpStatus.BAD_REQUEST));
        mvc.perform(as(user, post(base(user, tomorrow) + "/tasks"),
                        List.of(Map.of("name", "X", "startTime", "10:00", "durationMinutes", 10))))
                .andExpect(error(HttpStatus.BAD_REQUEST));
        sendChat(user, tomorrow, "hi").andExpect(error(HttpStatus.BAD_REQUEST));
    }

    @Test
    void pastDayWithoutPlanGivesEmptyReflectionAndCreatesNoPlan() throws Exception {
        TestUser user = newUser("reflect");
        LocalDate past = clock.today().minusDays(3);
        JsonNode view = ok(as(user, post(base(user, past))));
        assertTrue(view.get("exists").asBoolean());
        assertEquals(0, view.get("tasks").size());
        assertTrue(dailyRoutineRepository.findByUserAndDate(userRepository.findById(user.id()).orElseThrow(), past).isEmpty());
    }

    @Test
    void tasksOfAnotherUsersReflectionAreNotFoundAndNothingIsWritten() throws Exception {
        TestUser user = newUser("reflect");
        LocalDate today = clock.today();
        ok(as(user, post(base(user, today))));
        JsonNode mine = ok(as(user, post(base(user, today) + "/tasks"),
                List.of(Map.of("name", "Mine", "startTime", "08:00", "durationMinutes", 30))));
        String myTask = mine.get("tasks").get(0).get("id").asText();

        TestUser other = newUser("reflect");
        ok(as(other, post(base(other, today))));
        mvc.perform(as(other, put(base(other, today) + "/tasks"), List.of(Map.of(
                        "id", myTask, "name", "Hacked", "startTime", "08:00", "durationMinutes", 30))))
                .andExpect(error(HttpStatus.NOT_FOUND));
        mvc.perform(as(other, delete(base(other, today) + "/tasks").param("taskIds", myTask)))
                .andExpect(error(HttpStatus.NOT_FOUND));

        assertEquals("Mine", ok(as(user, get(base(user, today)))).get("tasks").get(0).get("name").asText());
    }

    @Test
    void invalidTasksAreRefused() throws Exception {
        TestUser user = newUser("reflect");
        LocalDate today = clock.today();
        ok(as(user, post(base(user, today))));
        String tasks = base(user, today) + "/tasks";
        mvc.perform(as(user, post(tasks), List.of(Map.of("name", " ", "startTime", "08:00", "durationMinutes", 30))))
                .andExpect(error(HttpStatus.BAD_REQUEST));
        mvc.perform(as(user, post(tasks), List.of(Map.of("name", "A", "startTime", "08:00", "durationMinutes", 0))))
                .andExpect(error(HttpStatus.BAD_REQUEST));
        mvc.perform(as(user, post(tasks), List.of(Map.of("name", "A", "startTime", "08:00", "durationMinutes", 1441))))
                .andExpect(error(HttpStatus.BAD_REQUEST));
        mvc.perform(as(user, post(tasks), List.of(Map.of("name", "A", "durationMinutes", 30))))
                .andExpect(error(HttpStatus.BAD_REQUEST));
        mvc.perform(as(user, post(tasks), List.of(Map.of(
                        "name", "A", "startTime", "08:00", "durationMinutes", 30, "color", "nope"))))
                .andExpect(error(HttpStatus.BAD_REQUEST));
        assertEquals(0, ok(as(user, get(base(user, today)))).get("tasks").size());
    }

    @Test
    void editsBeforeCreationAreNotFound() throws Exception {
        TestUser user = newUser("reflect");
        LocalDate today = clock.today();
        mvc.perform(as(user, post(base(user, today) + "/tasks"),
                        List.of(Map.of("name", "A", "startTime", "08:00", "durationMinutes", 30))))
                .andExpect(error(HttpStatus.NOT_FOUND));
        mvc.perform(as(user, post(base(user, today) + "/feedback"))).andExpect(error(HttpStatus.NOT_FOUND));
    }

    @Test
    void unknownUserIsNotFoundAndNoTokenIsNotAuthenticated() throws Exception {
        TestUser user = newUser("reflect");
        mvc.perform(as(user, get("/api/routine-manager/daily-reflection/" + UUID.randomUUID() + "/" + clock.today())))
                .andExpect(error(HttpStatus.NOT_FOUND));
        mvc.perform(get(base(user, clock.today()))).andExpect(error(HttpStatus.UNAUTHORIZED));
    }

    // ---------- US2: Buddy's note ----------

    @Test
    void feedbackIsStoredAndGoesStaleAfterAnEdit() throws Exception {
        TestUser user = newUser("reflect");
        LocalDate today = clock.today();
        seedPlan(user, today, task("Gym", "07:00", 60, null));
        JsonNode view = ok(as(user, post(base(user, today))));
        assertTrue(view.get("feedback").isNull());

        JsonNode withNote = ok(as(user, post(base(user, today) + "/feedback")));
        JsonNode feedback = withNote.get("feedback");
        assertTrue(feedback.get("text").asText().startsWith("Nice work today"));
        assertFalse(feedback.get("stale").asBoolean());
        assertEquals(feedback.get("text").asText(), ok(as(user, get(base(user, today)))).at("/feedback/text").asText());

        String gymId = withNote.get("tasks").get(0).get("id").asText();
        // Staleness compares entity timestamps, which use the system clock: make sure they differ.
        Thread.sleep(5);
        JsonNode edited = ok(as(user, put(base(user, today) + "/tasks"), List.of(Map.of(
                "id", gymId, "name", "Gym", "startTime", "07:00", "durationMinutes", 45))));
        assertTrue(edited.at("/feedback/stale").asBoolean());
    }

    @Test
    void feedbackFailureIsServiceUnavailableAndKeepsTheOldNote() throws Exception {
        TestUser user = newUser("reflect");
        LocalDate today = clock.today();
        ok(as(user, post(base(user, today))));
        String first = ok(as(user, post(base(user, today) + "/feedback"))).at("/feedback/text").asText();

        doThrow(new RuntimeException("AI down")).when(reflectionAi).feedback(any(), any(), any());
        mvc.perform(as(user, post(base(user, today) + "/feedback"))).andExpect(error(HttpStatus.SERVICE_UNAVAILABLE));
        assertEquals(first, ok(as(user, get(base(user, today)))).at("/feedback/text").asText());
    }

    // ---------- US3: reflection chat ----------

    @Test
    void reflectionChatHasItsOwnThread() throws Exception {
        TestUser user = newUser("reflect");
        LocalDate today = clock.today();
        ok(as(user, post(base(user, today))));
        sendChat(user, today, "I skipped the gym").andExpect(status().isOk());
        sendChat(user, today, "and read for 30 minutes").andExpect(status().isOk());

        String reflectionHistory = "/api/ai/chat/daily-reflection/" + user.id() + "/" + today + "/messages";
        JsonNode history = ok(as(user, get(reflectionHistory)));
        assertEquals(4, history.get("messages").size());
        assertEquals("I skipped the gym", history.get("messages").get(0).get("content").asText());

        JsonNode daily = ok(as(user, get("/api/ai/chat/daily-routine/" + user.id() + "/" + today + "/messages")));
        assertEquals(0, daily.get("messages").size());

        JsonNode page = ok(as(user, get(reflectionHistory).param("limit", "2")));
        assertEquals(2, page.get("messages").size());
        assertTrue(page.get("hasMore").asBoolean());
    }

    @Test
    void chatNeedsAnExistingReflectionAndAMessage() throws Exception {
        TestUser user = newUser("reflect");
        LocalDate today = clock.today();
        sendChat(user, today, "hello").andExpect(error(HttpStatus.NOT_FOUND));
        ok(as(user, post(base(user, today))));
        sendChat(user, today, "  ").andExpect(error(HttpStatus.BAD_REQUEST));
    }

    // ---------- helpers ----------

    private static String base(TestUser user, LocalDate date) {
        return "/api/routine-manager/daily-reflection/" + user.id() + "/" + date;
    }

    /** Creates the plan for a day through the daily-routine API and adds the given tasks. */
    @SafeVarargs
    private JsonNode seedPlan(TestUser user, LocalDate date, Map<String, Object>... tasks) throws Exception {
        String routineId = ok(as(user, post("/api/routine-manager/daily-routine/" + user.id())
                .param("date", date.toString()))).get("id").asText();
        return addPlanTasks(user, routineId, tasks);
    }

    @SafeVarargs
    private JsonNode addPlanTasks(TestUser user, String routineId, Map<String, Object>... tasks) throws Exception {
        return ok(as(user, post("/api/routine-manager/daily-routine/" + routineId + "/tasks"), List.of(tasks)));
    }

    private String planId(TestUser user, LocalDate date) {
        return planFor(user, date).getId().toString();
    }

    /** The stored plan with its tasks loaded (tasks are lazy). */
    private DailyRoutine planFor(TestUser user, LocalDate date) {
        return tx.execute(status -> {
            DailyRoutine routine = dailyRoutineRepository
                    .findByUserAndDate(userRepository.findById(user.id()).orElseThrow(), date).orElseThrow();
            routine.getTasks().size();
            return routine;
        });
    }

    private static Map<String, Object> task(String name, String start, int minutes, String color) {
        Map<String, Object> task = new HashMap<>(Map.of("name", name, "startTime", start, "durationMinutes", minutes));
        if (color != null) {
            task.put("color", color);
        }
        return task;
    }

    private JsonNode ok(MockHttpServletRequestBuilder request) throws Exception {
        return read(mvc.perform(request).andExpect(status().isOk()));
    }

    private ResultActions sendChat(TestUser user, LocalDate date, String message) throws Exception {
        return mvc.perform(as(user, post("/api/ai/chat/daily-reflection/" + user.id()),
                Map.of("date", date.toString(), "message", message)));
    }
}
