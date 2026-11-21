package com.tom.payment.routinemanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;

import com.tom.payment.routinemanager.model.DailyRoutine;
import com.tom.payment.routinemanager.model.DailyTask;
import com.tom.payment.routinemanager.model.DefaultRoutine;
import com.tom.payment.routinemanager.model.RoutineTaskTemplate;
import com.tom.payment.routinemanager.model.User;
import com.tom.payment.routinemanager.repository.DefaultRoutineRepository;
import com.tom.payment.routinemanager.service.DailyRoutineService;
import com.tom.payment.routinemanager.service.DefaultRoutineService;
import com.tom.payment.routinemanager.service.UserService;
import com.tom.payment.routinemanager.service.aitools.DailyRoutineAiTools;
import com.tom.payment.routinemanager.service.aitools.DefaultRoutineAiTools;
import com.tom.payment.routinemanager.repository.RoutineTaskTemplateRepository;
import com.tom.payment.routinemanager.repository.DailyTaskRepository;

/** F005: stored, round-robin task colours (specs/F005-i-want-color-to-be-persisted). */
@SpringBootTest
@Transactional
public class TaskColorIntegrationTest {

    private static final List<String> PALETTE =
            List.of("green", "orange", "blue", "grey", "slate", "peach", "pink", "purple");

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private UserService userService;
    @Autowired private DefaultRoutineService defaultRoutineService;
    @Autowired private DailyRoutineService dailyRoutineService;
    @Autowired private DefaultRoutineRepository defaultRoutineRepository;
    @Autowired private RoutineTaskTemplateRepository templateRepository;
    @Autowired private DailyTaskRepository dailyTaskRepository;
    @Autowired private DefaultRoutineAiTools defaultTools;
    @Autowired private DailyRoutineAiTools dailyTools;

    private MockMvc mockMvc;
    private User user;
    private DefaultRoutine weekday;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        user = userService.createUserAndDefaultRoutine(new User("color-" + suffix, "color-" + suffix + "@example.com"));
        weekday = defaultRoutineRepository.findByUserAndRoutineType(user, DefaultRoutine.RoutineTypeEnum.DAILY).orElseThrow();
    }

    private static RoutineTaskTemplate template(String name, int hour, String color) {
        RoutineTaskTemplate task = new RoutineTaskTemplate();
        task.setName(name);
        task.setStartTime(LocalTime.of(hour, 0));
        task.setDurationMinutes(30);
        task.setColor(color);
        return task;
    }

    private static DailyTask daily(String name, int hour, String color) {
        DailyTask task = new DailyTask();
        task.setName(name);
        task.setStartTime(LocalTime.of(hour, 0));
        task.setDurationMinutes(30);
        task.setColor(color);
        return task;
    }

    private RoutineTaskTemplate addOne(String name, int hour, String color) {
        return defaultRoutineService.addDefaultTasks(weekday.getId(), List.of(template(name, hour, color))).get(0);
    }

    private String nextColor() {
        return defaultRoutineService.getOrCreateDefaultRoutine(user.getId(), DefaultRoutine.RoutineTypeEnum.DAILY).getNextColor();
    }

    // ── US1 ─────────────────────────────────────────────────────────────

    @Test
    void assignsRoundRobinAndWraps() {
        assertEquals("green", nextColor());
        List<String> colors = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            colors.add(addOne("Task " + i, i, null).getColor());
        }
        List<String> expected = new ArrayList<>(PALETTE);
        expected.addAll(List.of("green", "orange"));
        assertEquals(expected, colors);
        assertEquals("blue", nextColor());
    }

    @Test
    void keptPreselectionAdvancesButOtherColorsDoNot() {
        assertEquals("green", addOne("A", 6, "green").getColor()); // pre-selected kept → advances
        assertEquals("orange", nextColor());
        assertEquals("pink", addOne("B", 7, "pink").getColor());   // other palette id → no advance
        assertEquals("#FF0000", addOne("C", 8, "#FF0000").getColor()); // exact colour → no advance
        assertEquals("orange", nextColor());
        assertEquals("orange", addOne("D", 9, null).getColor());
    }

    @Test
    void sameNameInOneRequestSharesColor() {
        List<RoutineTaskTemplate> saved = defaultRoutineService.addDefaultTasks(weekday.getId(), List.of(
                template("Sleep", 23, null), template(" sleep ", 0, null), template("Read", 21, null)));
        assertEquals("green", saved.get(0).getColor());
        assertEquals("green", saved.get(1).getColor());
        assertEquals("orange", saved.get(2).getColor());
        assertEquals("blue", nextColor());
    }

    @Test
    void updatesKeepColorUnlessOneIsSent() {
        RoutineTaskTemplate gym = addOne("Gym", 7, null);
        RoutineTaskTemplate other = addOne("Other", 9, null);

        RoutineTaskTemplate moved = template("Gym renamed", 10, null);
        moved.setId(gym.getId());
        moved.setDurationMinutes(45);
        RoutineTaskTemplate saved = defaultRoutineService.updateDefaultTasks(List.of(moved)).get(0);
        assertEquals("green", saved.getColor());

        defaultRoutineService.deleteDefaultTasks(List.of(other.getId()));
        assertEquals("blue", nextColor()); // deletes don't rewind the sequence
        assertEquals("green", defaultRoutineService.getOrCreateDefaultRoutine(user.getId(), DefaultRoutine.RoutineTypeEnum.DAILY)
                .getTasks().stream().filter(t -> t.getId().equals(gym.getId())).findFirst().orElseThrow().getColor());
    }

    @Test
    void dailyTasksUseTheirRoutinesOwnCursor() {
        DailyRoutine day = dailyRoutineService.ensureDailyRoutine(user.getId(), java.time.LocalDate.now().plusDays(3));
        List<DailyTask> saved = dailyRoutineService.addDailyTasks(day.getId(), List.of(daily("X", 6, null), daily("Y", 7, null)));
        assertEquals(List.of("green", "orange"), saved.stream().map(DailyTask::getColor).toList());

        DailyTask update = daily("X", 8, null);
        update.setId(saved.get(0).getId());
        assertEquals("green", dailyRoutineService.updateDailyTasks(List.of(update)).get(0).getColor());
    }

    @Test
    void rejectsUnknownColorOnCreate() {
        assertThrows(ResponseStatusException.class, () -> addOne("Bad", 6, "gold"));
    }

    @Test
    void paletteEndpointReturnsConfiguredOrder() throws Exception {
        var result = mockMvc.perform(get("/api/v1/task-palette"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.colors.length()").value(8))
                .andExpect(jsonPath("$.colors[0].id").value("green"))
                .andExpect(jsonPath("$.colors[0].light.accent").value("#10A36B"))
                .andExpect(jsonPath("$.colors[0].dark.bg").value("#1B3836"))
                .andReturn();
        for (int i = 0; i < PALETTE.size(); i++) {
            assertEquals(PALETTE.get(i), com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.colors[" + i + "].id"));
        }
    }

    @Test
    void createWithUnknownColorReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/default-routine/{routineId}/tasks", weekday.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"name\":\"Bad\",\"startTime\":\"06:00\",\"durationMinutes\":30,\"color\":\"gold\"}]"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void routineResponsesIncludeNextColor() throws Exception {
        mockMvc.perform(get("/api/v1/default-routine/users/{userId}", user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextColor").value("green"))
                .andExpect(jsonPath("$.colorCursor").doesNotExist());
    }

    // ── US2 ─────────────────────────────────────────────────────────────

    private String putColor(String path, java.util.UUID id, String color) throws Exception {
        String body = "[{\"id\":\"" + id + "\",\"name\":\"T\",\"startTime\":\"06:00\",\"durationMinutes\":30,\"color\":\"" + color + "\"}]";
        return mockMvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getStatus() + "";
    }

    @Test
    void restUpdatesStoreChosenColorsAndRejectUnknownOnes() throws Exception {
        RoutineTaskTemplate task = addOne("T", 6, null);
        assertEquals("200", putColor("/api/v1/default-routine/tasks", task.getId(), "pink"));
        assertEquals("pink", templateRepository.findById(task.getId()).orElseThrow().getColor());
        assertEquals("200", putColor("/api/v1/default-routine/tasks", task.getId(), "#AA3300"));
        assertEquals("#AA3300", templateRepository.findById(task.getId()).orElseThrow().getColor());
        assertEquals("400", putColor("/api/v1/default-routine/tasks", task.getId(), "gold"));
        assertEquals("#AA3300", templateRepository.findById(task.getId()).orElseThrow().getColor());

        DailyRoutine day = dailyRoutineService.ensureDailyRoutine(user.getId(), java.time.LocalDate.now().plusDays(4));
        DailyTask d = dailyRoutineService.addDailyTasks(day.getId(), List.of(daily("T", 6, null))).get(0);
        assertEquals("200", putColor("/api/routine-manager/daily-routine/tasks", d.getId(), "purple"));
        assertEquals("purple", dailyTaskRepository.findById(d.getId()).orElseThrow().getColor());
        assertEquals("400", putColor("/api/routine-manager/daily-routine/tasks", d.getId(), "gold"));
        assertEquals("purple", dailyTaskRepository.findById(d.getId()).orElseThrow().getColor());
    }

    @Test
    void buddySetsPaletteColorsByNameOnly() {
        RoutineTaskTemplate task = addOne("Reading", 21, null);
        String uid = user.getId().toString();
        String rid = weekday.getId().toString();

        defaultTools.updateDefaultRoutineTasks(uid, rid, List.of(
                new DefaultRoutineAiTools.TaskUpdateInput(task.getId().toString(), null, null, null, null, "Purple")));
        assertEquals("purple", templateRepository.findById(task.getId()).orElseThrow().getColor());

        for (String bad : List.of("#ff0000", "gold")) {
            String reply = defaultTools.updateDefaultRoutineTasks(uid, rid, List.of(
                    new DefaultRoutineAiTools.TaskUpdateInput(task.getId().toString(), null, null, null, null, bad)));
            assertTrue(reply.contains("Available colors:"), reply);
            for (String label : List.of("Green", "Orange", "Blue", "Grey", "Slate", "Peach", "Pink", "Purple")) {
                assertTrue(reply.contains(label), reply);
            }
            assertEquals("purple", templateRepository.findById(task.getId()).orElseThrow().getColor());
        }

        defaultTools.addDefaultRoutineTasks(uid, rid, List.of(new DefaultRoutineAiTools.TaskInput("Walk", "", "18:00", 30, "blue")));
        assertEquals("blue", defaultRoutineRepository.findById(weekday.getId()).orElseThrow().getTasks().stream()
                .filter(t -> t.getName().equals("Walk")).findFirst().orElseThrow().getColor());
    }

    @Test
    void buddyDailyToolsAcceptPaletteColors() {
        java.time.LocalDate date = java.time.LocalDate.now().plusDays(5);
        dailyRoutineService.ensureDailyRoutine(user.getId(), date);
        String reply = dailyTools.addDailyTasks(user.getId().toString(), date.toString(),
                List.of(new DailyRoutineAiTools.DailyTaskInput("Nap", "", "14:00", 20, false, "pink")));
        assertTrue(reply.startsWith("Successfully"), reply);
        String bad = dailyTools.addDailyTasks(user.getId().toString(), date.toString(),
                List.of(new DailyRoutineAiTools.DailyTaskInput("Nap2", "", "15:00", 20, false, "gold")));
        assertTrue(bad.contains("Available colors:"), bad);
    }

    // ── US3 ─────────────────────────────────────────────────────────────

    @Test
    void dailyRoutineCopiesTemplateColorsAndSequence() {
        addOne("Gym", 7, null);              // green
        addOne("Work", 9, "pink");           // explicit
        java.time.LocalDate date = java.time.LocalDate.now().plusDays(7);
        while (date.getDayOfWeek().getValue() >= 6) date = date.plusDays(1); // weekday → weekday template

        DailyRoutine day = dailyRoutineService.ensureDailyRoutine(user.getId(), date);
        assertEquals("green", day.getTasks().stream().filter(t -> t.getName().equals("Gym")).findFirst().orElseThrow().getColor());
        assertEquals("pink", day.getTasks().stream().filter(t -> t.getName().equals("Work")).findFirst().orElseThrow().getColor());
        assertEquals("orange", day.getNextColor()); // continues the template's sequence

        DailyTask extra = dailyRoutineService.addDailyTasks(day.getId(), List.of(daily("Extra", 18, null))).get(0);
        assertEquals("orange", extra.getColor());

        // Recolouring the template later leaves existing days alone.
        RoutineTaskTemplate gym = defaultRoutineRepository.findById(weekday.getId()).orElseThrow().getTasks().stream()
                .filter(t -> t.getName().equals("Gym")).findFirst().orElseThrow();
        RoutineTaskTemplate change = template("Gym", 7, "blue");
        change.setId(gym.getId());
        defaultRoutineService.updateDefaultTasks(List.of(change));
        assertEquals("green", dailyRoutineService.getOrCreateDailyRoutine(user.getId(), date).getTasks().stream()
                .filter(t -> t.getName().equals("Gym")).findFirst().orElseThrow().getColor());
    }

    @Test
    void midnightSpawnCopiesColors() {
        addOne("Gym", 7, null);
        addOne("Rest", 12, null);
        DefaultRoutine weekend = defaultRoutineService.getOrCreateDefaultRoutine(user.getId(), DefaultRoutine.RoutineTypeEnum.WEEKEND);
        defaultRoutineService.addDefaultTasks(weekend.getId(), List.of(template("Gym", 7, null), template("Rest", 12, null)));

        dailyRoutineService.spawnDailyRoutineForUser(user);
        DailyRoutine today = dailyRoutineService.getOrCreateDailyRoutine(user.getId(), java.time.LocalDate.now());
        assertEquals(List.of("green", "orange"), today.getTasks().stream()
                .sorted(java.util.Comparator.comparing(DailyTask::getStartTime)).map(DailyTask::getColor).toList());
        assertEquals("blue", today.getNextColor());
    }
}
