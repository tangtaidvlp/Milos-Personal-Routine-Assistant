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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.tom.payment.routinemanager.repository.DailyTaskRepository;
import com.tom.payment.routinemanager.repository.RoutineTaskTemplateRepository;
import com.tom.payment.routinemanager.service.aitools.DailyRoutineAiTools;
import com.tom.payment.routinemanager.service.aitools.DefaultRoutineAiTools;
import com.tom.payment.routinemanager.support.BaseComponentTest;
import com.tom.payment.routinemanager.support.TestUser;

/**
 * Flow: tasks get a stored colour from the palette, users and Buddy can change it,
 * and new days keep the template's colours (F005). Round-robin and validation
 * variations are in unit/TaskColorServiceTest.
 */
class TaskColorComponentTest extends BaseComponentTest {

    private static final String DEFAULT = "/api/v1/default-routine";
    private static final String DAILY = "/api/routine-manager/daily-routine";
    private static final List<String> PALETTE = List.of("green", "orange", "blue", "grey", "slate", "peach", "pink", "purple");

    @Autowired private RoutineTaskTemplateRepository templateRepository;
    @Autowired private DailyTaskRepository dailyTaskRepository;
    @Autowired private DefaultRoutineAiTools defaultTools;
    @Autowired private DailyRoutineAiTools dailyTools;
    @Autowired private TransactionTemplate tx;

    @Test
    void aNewTaskGetsTheNextPaletteColourAndTheRoutineShowsWhatComesNext() throws Exception {
        TestUser user = newUser("color");
        JsonNode routine = weekday(user);
        assertEquals("green", routine.get("nextColor").asText());
        assertFalse(routine.has("colorCursor"));

        JsonNode created = addTemplateTasks(user, routine.get("id").asText(), List.of(task("Gym", "07:00", null)));
        assertEquals("green", created.get(0).get("color").asText());
        assertEquals("orange", weekday(user).get("nextColor").asText());
    }

    @Test
    void usersCanChooseAColourButNotAnUnknownOne() throws Exception {
        TestUser user = newUser("color");
        String routineId = weekday(user).get("id").asText();
        String taskId = addTemplateTasks(user, routineId, List.of(task("T", "06:00", null))).get(0).get("id").asText();

        mvc.perform(as(user, post(DEFAULT + "/" + routineId + "/tasks"), List.of(task("Bad", "08:00", "gold"))))
                .andExpect(error(HttpStatus.BAD_REQUEST));

        putColor(user, DEFAULT + "/tasks", taskId, "pink").andExpect(status().isOk());
        assertEquals("pink", templateRepository.findById(UUID.fromString(taskId)).orElseThrow().getColor());
        putColor(user, DEFAULT + "/tasks", taskId, "#AA3300").andExpect(status().isOk());
        assertEquals("#AA3300", templateRepository.findById(UUID.fromString(taskId)).orElseThrow().getColor());
        putColor(user, DEFAULT + "/tasks", taskId, "gold").andExpect(error(HttpStatus.BAD_REQUEST));
        assertEquals("#AA3300", templateRepository.findById(UUID.fromString(taskId)).orElseThrow().getColor());

        String dayId = ensureDay(user, clock.today().plusDays(4)).get("id").asText();
        JsonNode dailyTask = read(mvc.perform(as(user, post(DAILY + "/" + dayId + "/tasks"), List.of(task("T", "06:00", null))))
                .andExpect(status().isOk())).get(0);
        String dailyTaskId = dailyTask.get("id").asText();
        putColor(user, DAILY + "/tasks", dailyTaskId, "purple").andExpect(status().isOk());
        assertEquals("purple", dailyTaskRepository.findById(UUID.fromString(dailyTaskId)).orElseThrow().getColor());
        putColor(user, DAILY + "/tasks", dailyTaskId, "gold").andExpect(error(HttpStatus.BAD_REQUEST));
        assertEquals("purple", dailyTaskRepository.findById(UUID.fromString(dailyTaskId)).orElseThrow().getColor());
    }

    @Test
    void updatesKeepTheColourUnlessOneIsSentAndDeletesDoNotRewind() throws Exception {
        TestUser user = newUser("color");
        String routineId = weekday(user).get("id").asText();
        JsonNode created = addTemplateTasks(user, routineId, List.of(task("Gym", "07:00", null), task("Other", "09:00", null)));
        String gymId = created.get(0).get("id").asText();
        String otherId = created.get(1).get("id").asText();

        mvc.perform(as(user, put(DEFAULT + "/tasks"), List.of(Map.of(
                        "id", gymId, "name", "Gym renamed", "startTime", "10:00", "durationMinutes", 45))))
                .andExpect(status().isOk());
        assertEquals("green", templateRepository.findById(UUID.fromString(gymId)).orElseThrow().getColor());

        mvc.perform(as(user, delete(DEFAULT + "/tasks").param("taskIds", otherId)))
                .andExpect(status().isNoContent());
        assertEquals("blue", weekday(user).get("nextColor").asText());

        String dayId = ensureDay(user, clock.today().plusDays(3)).get("id").asText();
        JsonNode daily = read(mvc.perform(as(user, post(DAILY + "/" + dayId + "/tasks"), List.of(task("X", "06:00", null))))
                .andExpect(status().isOk())).get(0);
        String color = daily.get("color").asText();
        mvc.perform(as(user, put(DAILY + "/tasks"), List.of(Map.of(
                        "id", daily.get("id").asText(), "name", "X", "startTime", "08:00", "durationMinutes", 30))))
                .andExpect(status().isOk());
        assertEquals(color, dailyTaskRepository.findById(UUID.fromString(daily.get("id").asText())).orElseThrow().getColor());
    }

    @Test
    void paletteIsServedInRoundRobinOrder() throws Exception {
        TestUser user = newUser("color");
        JsonNode body = read(mvc.perform(as(user, get("/api/v1/task-palette"))).andExpect(status().isOk()));
        JsonNode colors = body.get("colors");
        assertEquals(PALETTE, StreamSupport.stream(colors.spliterator(), false).map(c -> c.get("id").asText()).toList());
        assertEquals("#10A36B", colors.get(0).at("/light/accent").asText());
        assertEquals("#1B3836", colors.get(0).at("/dark/bg").asText());
    }

    @Test
    void aNewDayCopiesTheTemplateColoursAndKeepsThemWhenTheTemplateChanges() throws Exception {
        TestUser user = newUser("color");
        String routineId = weekday(user).get("id").asText();
        String gymId = addTemplateTasks(user, routineId, List.of(task("Gym", "07:00", null))).get(0).get("id").asText();
        addTemplateTasks(user, routineId, List.of(task("Work", "09:00", "pink")));
        LocalDate nextWednesday = clock.today().plusDays(7);

        JsonNode day = ensureDay(user, nextWednesday);
        assertEquals("green", byName(day.get("tasks"), "Gym").get("color").asText());
        assertEquals("pink", byName(day.get("tasks"), "Work").get("color").asText());
        assertEquals("orange", day.get("nextColor").asText());

        JsonNode extra = read(mvc.perform(as(user, post(DAILY + "/" + day.get("id").asText() + "/tasks"),
                List.of(task("Extra", "18:00", null)))).andExpect(status().isOk())).get(0);
        assertEquals("orange", extra.get("color").asText());

        putColor(user, DEFAULT + "/tasks", gymId, "blue").andExpect(status().isOk());
        JsonNode sameDay = read(mvc.perform(as(user, get(DAILY + "/" + user.id()).param("date", nextWednesday.toString())))
                .andExpect(status().isOk()));
        assertEquals("green", byName(sameDay.get("tasks"), "Gym").get("color").asText());
    }

    @Test
    void theMidnightSpawnCopiesColours() throws Exception {
        TestUser user = newUser("color");
        String routineId = weekday(user).get("id").asText();
        addTemplateTasks(user, routineId, List.of(task("Gym", "07:00", null), task("Rest", "12:00", null)));

        mvc.perform(as(user, post("/api/support/cron/spawn-daily-task"))).andExpect(status().isOk());

        JsonNode today = read(mvc.perform(as(user, get(DAILY + "/" + user.id()))).andExpect(status().isOk()));
        List<String> colors = StreamSupport.stream(today.get("tasks").spliterator(), false)
                .sorted(Comparator.comparing(t -> t.get("startTime").asText()))
                .map(t -> t.get("color").asText()).toList();
        assertEquals(List.of("green", "orange"), colors);
        assertEquals("blue", today.get("nextColor").asText());
    }

    @Test
    void buddySetsPaletteColoursByNameOnly() throws Exception {
        TestUser user = newUser("color");
        String routineId = weekday(user).get("id").asText();
        String taskId = addTemplateTasks(user, routineId, List.of(task("Reading", "21:00", null))).get(0).get("id").asText();
        String uid = user.id().toString();

        // Tool calls run in a transaction like Buddy's open session in local/fast. Without one, the
        // update/delete tools fail with LazyInitializationException (bug filed by F008, T058).
        tx.executeWithoutResult(s -> defaultTools.updateDefaultRoutineTasks(uid, routineId, List.of(
                new DefaultRoutineAiTools.TaskUpdateInput(taskId, null, null, null, null, "Purple"))));
        assertEquals("purple", templateRepository.findById(UUID.fromString(taskId)).orElseThrow().getColor());

        for (String bad : List.of("#ff0000", "gold")) {
            String reply = tx.execute(s -> defaultTools.updateDefaultRoutineTasks(uid, routineId, List.of(
                    new DefaultRoutineAiTools.TaskUpdateInput(taskId, null, null, null, null, bad))));
            assertTrue(reply.contains("Available colors:"), reply);
            for (String label : List.of("Green", "Orange", "Blue", "Grey", "Slate", "Peach", "Pink", "Purple")) {
                assertTrue(reply.contains(label), reply);
            }
            assertEquals("purple", templateRepository.findById(UUID.fromString(taskId)).orElseThrow().getColor());
        }

        tx.executeWithoutResult(s -> defaultTools.addDefaultRoutineTasks(uid, routineId,
                List.of(new DefaultRoutineAiTools.TaskInput("Walk", "", "18:00", 30, "blue"))));
        assertEquals("blue", byName(weekday(user).get("tasks"), "Walk").get("color").asText());
    }

    @Test
    void buddyDailyToolsAcceptPaletteColoursOnly() throws Exception {
        TestUser user = newUser("color");
        LocalDate date = clock.today().plusDays(5);
        ensureDay(user, date);
        String uid = user.id().toString();

        String ok = tx.execute(s -> dailyTools.addDailyTasks(uid, date.toString(),
                List.of(new DailyRoutineAiTools.DailyTaskInput("Nap", "", "14:00", 20, false, "pink"))));
        assertTrue(ok.startsWith("Successfully"), ok);

        String refused = tx.execute(s -> dailyTools.addDailyTasks(uid, date.toString(),
                List.of(new DailyRoutineAiTools.DailyTaskInput("Nap2", "", "15:00", 20, false, "gold"))));
        assertTrue(refused.contains("Available colors:"), refused);
    }

    // ---------- helpers ----------

    private JsonNode weekday(TestUser user) throws Exception {
        return read(mvc.perform(as(user, get(DEFAULT + "/users/" + user.id()))).andExpect(status().isOk()));
    }

    private JsonNode addTemplateTasks(TestUser user, String routineId, List<Map<String, Object>> tasks) throws Exception {
        return read(mvc.perform(as(user, post(DEFAULT + "/" + routineId + "/tasks"), tasks)).andExpect(status().isOk()));
    }

    private JsonNode ensureDay(TestUser user, LocalDate date) throws Exception {
        return read(mvc.perform(as(user, post(DAILY + "/" + user.id()).param("date", date.toString())))
                .andExpect(status().isOk()));
    }

    private ResultActions putColor(TestUser user, String path, String id, String color)
            throws Exception {
        return mvc.perform(as(user, put(path), List.of(Map.of(
                "id", id, "name", "T", "startTime", "06:00", "durationMinutes", 30, "color", color))));
    }

    private static Map<String, Object> task(String name, String start, String color) {
        Map<String, Object> task = new HashMap<>(Map.of("name", name, "startTime", start, "durationMinutes", 30));
        if (color != null) {
            task.put("color", color);
        }
        return task;
    }

    private static JsonNode byName(JsonNode tasks, String name) {
        for (JsonNode task : tasks) {
            if (name.equals(task.get("name").asText())) {
                return task;
            }
        }
        throw new AssertionError("no task named " + name + " in " + tasks);
    }
}
