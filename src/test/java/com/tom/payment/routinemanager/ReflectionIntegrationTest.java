package com.tom.payment.routinemanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tom.payment.routinemanager.model.DailyRoutine;
import com.tom.payment.routinemanager.model.DailyTask;
import com.tom.payment.routinemanager.model.User;
import com.tom.payment.routinemanager.repository.DailyRoutineRepository;
import com.tom.payment.routinemanager.service.DailyRoutineService;
import com.tom.payment.routinemanager.service.UserService;
import com.tom.payment.routinemanager.service.reflection.ReflectionAiService;

/** F007: daily reflection (US1 copy + independence, US2 comparison + feedback, US3 chat). */
@SpringBootTest(properties = {
        "spring.ai.active=false",
        "app.chat.compaction-threshold=1000"
})
@ActiveProfiles("fast")
class ReflectionIntegrationTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Autowired private WebApplicationContext context;
    @Autowired private UserService userService;
    @Autowired private DailyRoutineService dailyRoutineService;
    @Autowired private DailyRoutineRepository dailyRoutineRepository;
    @Autowired private TransactionTemplate tx;
    @MockitoSpyBean private ReflectionAiService reflectionAi;

    private final ObjectMapper json = new ObjectMapper();
    private MockMvc mvc;
    private User user;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        user = newUser();
    }

    @AfterEach
    void resetSpy() {
        reset(reflectionAi);
    }

    // ---------- US1 ----------

    @Test
    void firstToggleCopiesThePlanOnceAndIsIdempotent() throws Exception {
        List<DailyTask> plan = seedPlan(TODAY, task("Gym", "07:00", 60, "green"), task("Work", "09:00", 240, null));

        JsonNode view = ok(post(base(TODAY)));
        assertTrue(view.get("exists").asBoolean());
        assertEquals(2, view.get("tasks").size());
        JsonNode gym = view.get("tasks").get(0);
        assertEquals("Gym", gym.get("name").asText());
        assertEquals("07:00", gym.get("startTime").asText());
        assertEquals("08:00", gym.get("endTime").asText());
        assertEquals(60, gym.get("durationMinutes").asInt());
        assertEquals("green", gym.get("color").asText());
        assertEquals(plan.get(0).getId().toString(), gym.get("plannedTaskId").asText());
        assertEquals(2, view.get("comparison").get("counts").get("asPlanned").asInt());

        // Plan changes after creation do not re-copy.
        dailyRoutineService.addDailyTasks(planFor(TODAY).getId(), new ArrayList<>(List.of(task("Read", "21:00", 30, null))));
        JsonNode again = ok(post(base(TODAY)));
        assertEquals(view.get("id").asText(), again.get("id").asText());
        assertEquals(2, again.get("tasks").size());
        assertEquals(1, again.get("comparison").get("counts").get("skipped").asInt());
    }

    @Test
    void getNeverCreatesAReflection() throws Exception {
        seedPlan(TODAY, task("Gym", "07:00", 60, null));
        JsonNode view = ok(get(base(TODAY)));
        assertFalse(view.get("exists").asBoolean());
        assertEquals(0, view.get("tasks").size());
        assertEquals(1, view.get("comparison").get("counts").get("skipped").asInt());
        assertFalse(ok(get(base(TODAY))).get("exists").asBoolean());
    }

    @Test
    void reflectionEditsNeverTouchThePlanAndViceVersa() throws Exception {
        seedPlan(TODAY, task("Gym", "07:00", 60, null), task("Work", "09:00", 240, null));
        JsonNode view = ok(post(base(TODAY)));
        String gymId = view.get("tasks").get(0).get("id").asText();
        String workId = view.get("tasks").get(1).get("id").asText();

        ok(put(base(TODAY) + "/tasks"), List.of(Map.of(
                "id", gymId, "name", "Short gym", "startTime", "07:30", "durationMinutes", 30)));
        ok(delete(base(TODAY) + "/tasks").param("taskIds", workId));
        JsonNode added = ok(post(base(TODAY) + "/tasks"), List.of(Map.of(
                "name", "Coffee with Sam", "startTime", "16:00", "durationMinutes", 45)));

        DailyRoutine plan = planFor(TODAY);
        assertEquals(2, plan.getTasks().size());
        DailyTask planGym = plan.getTasks().stream().filter(t -> t.getName().equals("Gym")).findFirst().orElseThrow();
        assertEquals(LocalTime.of(7, 0), planGym.getStartTime());
        assertEquals(60, planGym.getDurationMinutes());

        JsonNode counts = added.get("comparison").get("counts");
        assertEquals(0, counts.get("asPlanned").asInt());
        assertEquals(1, counts.get("changed").asInt());   // renamed + shortened, still matched
        assertEquals(1, counts.get("skipped").asInt());
        assertEquals(1, counts.get("unplanned").asInt());
        assertEquals(300, added.get("comparison").get("plannedMinutes").asInt());
        assertEquals(75, added.get("comparison").get("actualMinutes").asInt());
        JsonNode coffee = added.get("tasks").get(1);
        assertTrue(coffee.get("plannedTaskId").isNull());
        assertNotNull(coffee.get("color").asText(null)); // F005 auto-assigned

        // Plan edit afterwards: reflection unchanged.
        DailyTask edit = new DailyTask();
        edit.setId(planGym.getId());
        edit.setName("Gym");
        edit.setStartTime(LocalTime.of(6, 0));
        edit.setDurationMinutes(90);
        dailyRoutineService.updateDailyTasks(List.of(edit));
        JsonNode after = ok(get(base(TODAY)));
        assertEquals("07:30", after.get("tasks").get(0).get("startTime").asText());
        assertEquals(30, after.get("tasks").get(0).get("durationMinutes").asInt());
    }

    @Test
    void overlappingRealityIsKeptAsEntered() throws Exception {
        ok(post(base(TODAY)));
        JsonNode view = ok(post(base(TODAY) + "/tasks"), List.of(
                Map.of("name", "Call mum", "startTime", "10:00", "durationMinutes", 60),
                Map.of("name", "Laundry", "startTime", "10:30", "durationMinutes", 30)));
        assertEquals("10:00", view.get("tasks").get(0).get("startTime").asText());
        assertEquals("10:30", view.get("tasks").get(1).get("startTime").asText());
    }

    @Test
    void futureDatesAreRejected() throws Exception {
        LocalDate tomorrow = TODAY.plusDays(1);
        mvc.perform(post(base(tomorrow))).andExpect(status().isBadRequest());
        send(post(base(tomorrow) + "/tasks"), List.of(Map.of("name", "X", "startTime", "10:00", "durationMinutes", 10)))
                .andExpect(status().isBadRequest());
        sendChat(tomorrow, "hi").andExpect(status().isBadRequest());
    }

    @Test
    void pastDayWithoutPlanGivesEmptyReflectionAndCreatesNoPlan() throws Exception {
        LocalDate past = TODAY.minusDays(3);
        JsonNode view = ok(post(base(past)));
        assertTrue(view.get("exists").asBoolean());
        assertEquals(0, view.get("tasks").size());
        assertTrue(dailyRoutineRepository.findByUserAndDate(user, past).isEmpty());
    }

    @Test
    void tasksOfAnotherReflectionAre404AndNothingIsWritten() throws Exception {
        ok(post(base(TODAY)));
        JsonNode mine = ok(post(base(TODAY) + "/tasks"), List.of(Map.of("name", "Mine", "startTime", "08:00", "durationMinutes", 30)));
        String myTask = mine.get("tasks").get(0).get("id").asText();

        User other = newUser();
        String otherBase = "/api/routine-manager/daily-reflection/" + other.getId() + "/" + TODAY;
        mvc.perform(post(otherBase)).andExpect(status().isOk());
        send(put(otherBase + "/tasks"), List.of(Map.of("id", myTask, "name", "Hacked", "startTime", "08:00", "durationMinutes", 30)))
                .andExpect(status().isNotFound());
        mvc.perform(delete(otherBase + "/tasks").param("taskIds", myTask)).andExpect(status().isNotFound());

        JsonNode still = ok(get(base(TODAY)));
        assertEquals("Mine", still.get("tasks").get(0).get("name").asText());
    }

    @Test
    void validationErrorsAre400() throws Exception {
        ok(post(base(TODAY)));
        send(post(base(TODAY) + "/tasks"), List.of(Map.of("name", " ", "startTime", "08:00", "durationMinutes", 30)))
                .andExpect(status().isBadRequest());
        send(post(base(TODAY) + "/tasks"), List.of(Map.of("name", "A", "startTime", "08:00", "durationMinutes", 0)))
                .andExpect(status().isBadRequest());
        send(post(base(TODAY) + "/tasks"), List.of(Map.of("name", "A", "startTime", "08:00", "durationMinutes", 1441)))
                .andExpect(status().isBadRequest());
        send(post(base(TODAY) + "/tasks"), List.of(Map.of("name", "A", "durationMinutes", 30)))
                .andExpect(status().isBadRequest());
        send(post(base(TODAY) + "/tasks"), List.of(Map.of("name", "A", "startTime", "08:00", "durationMinutes", 30, "color", "nope")))
                .andExpect(status().isBadRequest());
        assertEquals(0, ok(get(base(TODAY))).get("tasks").size());
    }

    @Test
    void editsBeforeCreationAre404() throws Exception {
        send(post(base(TODAY) + "/tasks"), List.of(Map.of("name", "A", "startTime", "08:00", "durationMinutes", 30)))
                .andExpect(status().isNotFound());
        mvc.perform(post(base(TODAY) + "/feedback")).andExpect(status().isNotFound());
    }

    @Test
    void unknownUserIs404() throws Exception {
        mvc.perform(get("/api/routine-manager/daily-reflection/" + UUID.randomUUID() + "/" + TODAY))
                .andExpect(status().isNotFound());
    }

    // ---------- US2 ----------

    @Test
    void feedbackIsStoredAndGoesStaleAfterAnEdit() throws Exception {
        seedPlan(TODAY, task("Gym", "07:00", 60, null));
        JsonNode view = ok(post(base(TODAY)));
        assertTrue(view.get("feedback").isNull());

        JsonNode withNote = ok(post(base(TODAY) + "/feedback"));
        JsonNode feedback = withNote.get("feedback");
        assertTrue(feedback.get("text").asText().startsWith("Nice work today"));
        assertFalse(feedback.get("stale").asBoolean());
        assertEquals(feedback.get("text").asText(), ok(get(base(TODAY))).get("feedback").get("text").asText());

        String gymId = withNote.get("tasks").get(0).get("id").asText();
        Thread.sleep(5);
        JsonNode edited = ok(put(base(TODAY) + "/tasks"), List.of(Map.of(
                "id", gymId, "name", "Gym", "startTime", "07:00", "durationMinutes", 45)));
        assertTrue(edited.get("feedback").get("stale").asBoolean());
    }

    @Test
    void feedbackFailureIs503AndKeepsTheOldNote() throws Exception {
        ok(post(base(TODAY)));
        String first = ok(post(base(TODAY) + "/feedback")).get("feedback").get("text").asText();

        doThrow(new RuntimeException("AI down")).when(reflectionAi).feedback(any());
        mvc.perform(post(base(TODAY) + "/feedback")).andExpect(status().isServiceUnavailable());
        assertEquals(first, ok(get(base(TODAY))).get("feedback").get("text").asText());
    }

    // ---------- US3 ----------

    @Test
    void reflectionChatHasItsOwnThread() throws Exception {
        ok(post(base(TODAY)));
        sendChat(TODAY, "I skipped the gym").andExpect(status().isOk());
        sendChat(TODAY, "and read for 30 minutes").andExpect(status().isOk());

        JsonNode history = ok(get("/api/ai/chat/daily-reflection/" + user.getId() + "/" + TODAY + "/messages"));
        assertEquals(4, history.get("messages").size());
        assertEquals("I skipped the gym", history.get("messages").get(0).get("content").asText());

        JsonNode daily = ok(get("/api/ai/chat/daily-routine/" + user.getId() + "/" + TODAY + "/messages"));
        assertEquals(0, daily.get("messages").size());

        JsonNode page = ok(get("/api/ai/chat/daily-reflection/" + user.getId() + "/" + TODAY + "/messages").param("limit", "2"));
        assertEquals(2, page.get("messages").size());
        assertTrue(page.get("hasMore").asBoolean());
    }

    @Test
    void chatNeedsAnExistingReflectionAndAMessage() throws Exception {
        sendChat(TODAY, "hello").andExpect(status().isNotFound());
        ok(post(base(TODAY)));
        sendChat(TODAY, "  ").andExpect(status().isBadRequest());
    }

    // ---------- helpers ----------

    private List<DailyTask> seedPlan(LocalDate date, DailyTask... tasks) {
        DailyRoutine routine = dailyRoutineService.ensureDailyRoutine(user.getId(), date);
        return dailyRoutineService.addDailyTasks(routine.getId(), new ArrayList<>(List.of(tasks)));
    }

    /** Plan with its tasks loaded (read in a transaction; tasks are lazy). */
    private DailyRoutine planFor(LocalDate date) {
        return tx.execute(status -> {
            DailyRoutine routine = dailyRoutineRepository.findByUserAndDate(user, date).orElseThrow();
            routine.getTasks().size();
            return routine;
        });
    }

    private static DailyTask task(String name, String start, int minutes, String color) {
        DailyTask t = new DailyTask();
        t.setName(name);
        t.setStartTime(LocalTime.parse(start));
        t.setDurationMinutes(minutes);
        t.setColor(color);
        return t;
    }

    private String base(LocalDate date) {
        return "/api/routine-manager/daily-reflection/" + user.getId() + "/" + date;
    }

    private JsonNode ok(MockHttpServletRequestBuilder request) throws Exception {
        String body = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private JsonNode ok(MockHttpServletRequestBuilder request, Object body) throws Exception {
        String response = send(request, body).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    private ResultActions send(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private ResultActions sendChat(LocalDate date, String message) throws Exception {
        return send(post("/api/ai/chat/daily-reflection/" + user.getId()), Map.of("date", date.toString(), "message", message));
    }

    private User newUser() {
        User u = new User();
        String tag = UUID.randomUUID().toString().substring(0, 8);
        u.setUsername("reflect-" + tag);
        u.setEmail("reflect-" + tag + "@example.test");
        return userService.createUserAndDefaultRoutine(u);
    }
}
