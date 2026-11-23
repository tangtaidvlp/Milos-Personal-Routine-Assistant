package com.tom.payment.routinemanager.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.tom.payment.routinemanager.model.User;
import com.tom.payment.routinemanager.repository.DailyRoutineRepository;
import com.tom.payment.routinemanager.repository.DailyTaskRepository;
import com.tom.payment.routinemanager.repository.UserRepository;
import com.tom.payment.routinemanager.service.aitools.DailyRoutineAiTools;
import com.tom.payment.routinemanager.support.BaseComponentTest;
import com.tom.payment.routinemanager.support.TestUser;

/**
 * Flow: a user's day is created from their template, and they edit its tasks.
 * Today is Wednesday 2026-11-18 (test clock). Template-choice variations are in
 * unit/RoutineTemplateRulesTest; past/future boundaries in unit/DayRulesTest.
 */
class DailyRoutineComponentTest extends BaseComponentTest {

    private static final String DEFAULT = "/api/v1/default-routine";
    private static final String DAILY = "/api/routine-manager/daily-routine";
    private static final LocalDate SATURDAY = LocalDate.of(2026, 11, 21);

    @Autowired private UserRepository userRepository;
    @Autowired private DailyRoutineRepository dailyRoutineRepository;
    @Autowired private DailyTaskRepository dailyTaskRepository;
    @Autowired private DailyRoutineAiTools dailyTools;
    @Autowired private TransactionTemplate tx;

    @Test
    void todayIsCopiedFromTheWeekdayTemplateAndStored() throws Exception {
        TestUser user = newUser("daily");
        addTemplateTasks(user, "DAILY", List.of(task("Morning Coffee", "08:00", 15)));

        JsonNode today = read(mvc.perform(as(user, get(DAILY + "/" + user.id()))).andExpect(status().isOk()));
        assertEquals(clock.today().toString(), today.get("date").asText());
        assertEquals(1, today.get("tasks").size());
        JsonNode coffee = today.get("tasks").get(0);
        assertEquals("Morning Coffee", coffee.get("name").asText());
        assertEquals("08:00", coffee.get("startTime").asText());
        assertFalse(coffee.get("completed").asBoolean());
        assertTrue(dailyRoutineRepository.findByUserAndDate(user(user), clock.today()).isPresent());
    }

    @Test
    void readingAnotherDayDoesNotCreateIt() throws Exception {
        TestUser user = newUser("daily");
        addTemplateTasks(user, "DAILY", List.of(task("Morning Coffee", "08:00", 15)));
        LocalDate nextWeek = clock.today().plusDays(7);

        JsonNode day = read(mvc.perform(as(user, get(DAILY + "/" + user.id()).param("date", nextWeek.toString())))
                .andExpect(status().isOk()));
        assertEquals(0, day.get("tasks").size());
        assertTrue(dailyRoutineRepository.findByUserAndDate(user(user), nextWeek).isEmpty());
    }

    @Test
    void aSaturdayIsCopiedFromTheWeekendTemplateWhenItHasTasks() throws Exception {
        TestUser user = newUser("daily");
        addTemplateTasks(user, "DAILY", List.of(task("Work", "09:00", 240)));
        addTemplateTasks(user, "WEEKEND", List.of(task("Long run", "09:00", 60), task("Brunch", "11:00", 60)));

        JsonNode saturday = ensureDay(user, SATURDAY);
        assertEquals(List.of("Long run", "Brunch"), names(saturday.get("tasks")));
    }

    @Test
    void tasksCanBeAddedCompletedRenamedAndDeleted() throws Exception {
        TestUser user = newUser("daily");
        LocalDate day = clock.today().plusDays(1);
        String routineId = ensureDay(user, day).get("id").asText();

        JsonNode added = read(mvc.perform(as(user, post(DAILY + "/" + routineId + "/tasks"), List.of(
                        task("Daily Task A", "09:00", 30), task("Daily Task B", "10:00", 60))))
                .andExpect(status().isOk()));
        String a = added.get(0).get("id").asText();
        String b = added.get(1).get("id").asText();

        mvc.perform(as(user, put(DAILY + "/tasks"), List.of(Map.of(
                        "id", a, "name", "Daily Task A Updated", "startTime", "09:00", "durationMinutes", 30,
                        "completed", true))))
                .andExpect(status().isOk());
        var stored = dailyTaskRepository.findById(UUID.fromString(a)).orElseThrow();
        assertEquals("Daily Task A Updated", stored.getName());
        assertTrue(stored.isCompleted());

        mvc.perform(as(user, delete(DAILY + "/tasks").param("taskIds", a, b))).andExpect(status().isNoContent());
        assertTrue(dailyTaskRepository.findById(UUID.fromString(a)).isEmpty());
        assertTrue(dailyTaskRepository.findById(UUID.fromString(b)).isEmpty());
    }

    @Test
    void buddyCannotRescheduleOrAddToAPastDay() throws Exception {
        TestUser user = newUser("daily");
        LocalDate yesterday = clock.today().minusDays(1);
        String routineId = ensureDay(user, yesterday).get("id").asText();
        JsonNode added = read(mvc.perform(as(user, post(DAILY + "/" + routineId + "/tasks"),
                List.of(task("Gym", "07:00", 60)))).andExpect(status().isOk()));
        String gymId = added.get(0).get("id").asText();

        String addReply = dailyTools.addDailyTasks(user.id().toString(), yesterday.toString(),
                List.of(new DailyRoutineAiTools.DailyTaskInput("Nap", "", "14:00", 20, false, null)));
        assertTrue(addReply.startsWith("Cannot add"), addReply);

        // Inside a transaction, like the open session Buddy gets in local/fast runs. Without one (prod has
        // open-in-view off) this tool fails with LazyInitializationException: filed as a bug by F008 (T058).
        String updateReply = tx.execute(status -> dailyTools.updateDailyTasks(user.id().toString(), yesterday.toString(),
                List.of(new DailyRoutineAiTools.DailyTaskUpdateInput(gymId, "Gym (done)", null, "10:00", 15, true, null))));
        assertTrue(updateReply.startsWith("Successfully"), updateReply);

        var gym = dailyTaskRepository.findById(UUID.fromString(gymId)).orElseThrow();
        assertEquals("Gym (done)", gym.getName());
        assertTrue(gym.isCompleted());
        assertEquals(LocalTime.of(7, 0), gym.getStartTime());
        assertEquals(60, gym.getDurationMinutes());
    }

    @Test
    void anOvernightTaskIsStoredAsOneTask() throws Exception {
        TestUser user = newUser("daily");
        LocalDate day = clock.today().plusDays(1);
        String routineId = ensureDay(user, day).get("id").asText();

        JsonNode created = read(mvc.perform(as(user, post(DAILY + "/" + routineId + "/tasks"),
                List.of(task("Sleep", "22:00", 480)))).andExpect(status().isOk()));
        assertEquals("06:00", created.get(0).get("endTime").asText());
        String sleepId = created.get(0).get("id").asText();

        mvc.perform(as(user, put(DAILY + "/tasks"), List.of(Map.of(
                        "id", sleepId, "name", "Sleep", "startTime", "23:00", "durationMinutes", 480))))
                .andExpect(status().isOk());
        JsonNode tasks = day(user, day).get("tasks");
        assertEquals(1, tasks.size());
        assertEquals("23:00", tasks.get(0).get("startTime").asText());
        assertEquals("07:00", tasks.get(0).get("endTime").asText());
    }

    @Test
    void durationOutsideOneDayIsRefused() throws Exception {
        TestUser user = newUser("daily");
        LocalDate day = clock.today().plusDays(1);
        String routineId = ensureDay(user, day).get("id").asText();
        for (int minutes : new int[] {1441, 0}) {
            JsonNode refusal = read(mvc.perform(as(user, post(DAILY + "/" + routineId + "/tasks"),
                            List.of(task("Bad", "22:00", minutes))))
                    .andExpect(error(HttpStatus.BAD_REQUEST)));
            assertEquals("durationMinutes must be between 1 and 1440", refusal.get("message").asText());
        }
        assertEquals(0, day(user, day).get("tasks").size());
    }

    @Test
    void anOvernightTemplateTaskIsCopiedToTheDayAsOneTask() throws Exception {
        TestUser user = newUser("daily");
        addTemplateTasks(user, "DAILY", List.of(task("Sleep", "22:00", 480)));

        JsonNode tasks = ensureDay(user, clock.today().plusDays(1)).get("tasks");
        assertEquals(List.of("Sleep"), names(tasks));
        assertEquals(480, tasks.get(0).get("durationMinutes").asInt());
        assertEquals("06:00", tasks.get(0).get("endTime").asText());
    }

    @Test
    void requestWithoutTokenIsNotAuthenticated() throws Exception {
        TestUser user = newUser("daily");
        mvc.perform(get(DAILY + "/" + user.id())).andExpect(error(HttpStatus.UNAUTHORIZED));
    }

    // ---------- Reset day (specs/F012) ----------

    @Test
    void resetReplacesAnEditedDayWithAFreshCopyOfTheWeekdayTemplate() throws Exception {
        TestUser user = newUser("reset");
        addTemplateTasks(user, "DAILY", List.of(task("Coffee", "08:00", 15), task("Work", "09:00", 240)));
        LocalDate today = clock.today();
        JsonNode plan = ensureDay(user, today);
        String coffeeId = plan.get("tasks").get(0).get("id").asText();
        mvc.perform(as(user, put(DAILY + "/tasks"), List.of(Map.of(
                        "id", coffeeId, "name", "Coffee", "startTime", "10:00", "durationMinutes", 15, "completed", true))))
                .andExpect(status().isOk());
        mvc.perform(as(user, post(DAILY + "/" + plan.get("id").asText() + "/tasks"), List.of(task("Extra", "20:00", 30))))
                .andExpect(status().isOk());

        mvc.perform(as(user, post(resetPath(user, today)))).andExpect(status().isNoContent());

        JsonNode tasks = day(user, today).get("tasks");
        JsonNode template = read(mvc.perform(as(user, get(DEFAULT + "/users/" + user.id()).param("type", "DAILY")))
                .andExpect(status().isOk())).get("tasks");
        assertEquals(List.of("Coffee", "Work"), names(tasks));
        for (int i = 0; i < tasks.size(); i++) {
            JsonNode copy = tasks.get(i);
            JsonNode source = template.get(i);
            assertEquals(source.get("startTime").asText(), copy.get("startTime").asText());
            assertEquals(source.get("durationMinutes").asInt(), copy.get("durationMinutes").asInt());
            assertEquals(source.get("color").asText(), copy.get("color").asText());
            assertFalse(copy.get("completed").asBoolean());
        }
    }

    @Test
    void aSaturdayResetsToTheWeekendTemplateOrToTheWeekdayOneWhenWeekendIsEmpty() throws Exception {
        TestUser withWeekend = newUser("reset");
        addTemplateTasks(withWeekend, "DAILY", List.of(task("Work", "09:00", 240)));
        addTemplateTasks(withWeekend, "WEEKEND", List.of(task("Long run", "09:00", 60)));
        mvc.perform(as(withWeekend, post(resetPath(withWeekend, SATURDAY)))).andExpect(status().isNoContent());
        assertEquals(List.of("Long run"), names(day(withWeekend, SATURDAY).get("tasks")));

        TestUser emptyWeekend = newUser("reset");
        addTemplateTasks(emptyWeekend, "DAILY", List.of(task("Work", "09:00", 240)));
        mvc.perform(as(emptyWeekend, post(resetPath(emptyWeekend, SATURDAY)))).andExpect(status().isNoContent());
        assertEquals(List.of("Work"), names(day(emptyWeekend, SATURDAY).get("tasks")));
    }

    @Test
    void resettingAFutureDayThatDoesNotExistYetCreatesItFromTheTemplate() throws Exception {
        TestUser user = newUser("reset");
        addTemplateTasks(user, "DAILY", List.of(task("Coffee", "08:00", 15)));
        LocalDate tomorrow = clock.today().plusDays(1);

        mvc.perform(as(user, post(resetPath(user, tomorrow)))).andExpect(status().isNoContent());

        assertTrue(dailyRoutineRepository.findByUserAndDate(user(user), tomorrow).isPresent());
        assertEquals(List.of("Coffee"), names(day(user, tomorrow).get("tasks")));
    }

    @Test
    void aPastDayCannotBeResetAndStaysAsItWas() throws Exception {
        TestUser user = newUser("reset");
        addTemplateTasks(user, "DAILY", List.of(task("Coffee", "08:00", 15)));
        LocalDate yesterday = clock.today().minusDays(1);
        String routineId = ensureDay(user, yesterday).get("id").asText();
        mvc.perform(as(user, post(DAILY + "/" + routineId + "/tasks"), List.of(task("Extra", "20:00", 30))))
                .andExpect(status().isOk());

        JsonNode refusal = read(mvc.perform(as(user, post(resetPath(user, yesterday))))
                .andExpect(error(HttpStatus.BAD_REQUEST)));
        assertEquals("Past days can't be reset", refusal.get("message").asText());
        assertEquals(List.of("Coffee", "Extra"), names(day(user, yesterday).get("tasks")));
    }

    @Test
    void anotherUsersDayCannotBeReset() throws Exception {
        TestUser owner = newUser("reset");
        TestUser other = newUser("reset");
        mvc.perform(as(other, post(resetPath(owner, clock.today())))).andExpect(error(HttpStatus.FORBIDDEN));
    }

    @Test
    void resetKeepsTheDaysReflection() throws Exception {
        TestUser user = newUser("reset");
        addTemplateTasks(user, "DAILY", List.of(task("Coffee", "08:00", 15)));
        LocalDate today = clock.today();
        ensureDay(user, today);
        String reflection = "/api/routine-manager/daily-reflection/" + user.id() + "/" + today;
        mvc.perform(as(user, post(reflection))).andExpect(status().isOk());
        mvc.perform(as(user, post(reflection + "/tasks"), List.of(task("Real nap", "14:00", 20))))
                .andExpect(status().isOk());

        mvc.perform(as(user, post(resetPath(user, today)))).andExpect(status().isNoContent());

        JsonNode reality = read(mvc.perform(as(user, get(reflection))).andExpect(status().isOk()));
        assertEquals(List.of("Coffee", "Real nap"), names(reality.get("tasks")));
    }

    // ---------- helpers ----------

    private static String resetPath(TestUser user, LocalDate date) {
        return DAILY + "/" + user.id() + "/" + date + "/reset";
    }

    private User user(TestUser user) {
        return userRepository.findById(user.id()).orElseThrow();
    }

    private void addTemplateTasks(TestUser user, String type, List<Map<String, Object>> tasks) throws Exception {
        JsonNode routine = read(mvc.perform(as(user, get(DEFAULT + "/users/" + user.id()).param("type", type)))
                .andExpect(status().isOk()));
        mvc.perform(as(user, post(DEFAULT + "/" + routine.get("id").asText() + "/tasks"), tasks))
                .andExpect(status().isOk());
    }

    private JsonNode ensureDay(TestUser user, LocalDate date) throws Exception {
        return read(mvc.perform(as(user, post(DAILY + "/" + user.id()).param("date", date.toString())))
                .andExpect(status().isOk()));
    }

    private JsonNode day(TestUser user, LocalDate date) throws Exception {
        return read(mvc.perform(as(user, get(DAILY + "/" + user.id()).param("date", date.toString())))
                .andExpect(status().isOk()));
    }

    private static Map<String, Object> task(String name, String start, int minutes) {
        return Map.of("name", name, "startTime", start, "durationMinutes", minutes);
    }

    private static List<String> names(JsonNode tasks) {
        return java.util.stream.StreamSupport.stream(tasks.spliterator(), false)
                .map(t -> t.get("name").asText()).toList();
    }
}
