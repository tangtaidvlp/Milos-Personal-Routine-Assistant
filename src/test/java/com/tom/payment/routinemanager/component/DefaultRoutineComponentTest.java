package com.tom.payment.routinemanager.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.databind.JsonNode;
import com.tom.payment.routinemanager.support.BaseComponentTest;
import com.tom.payment.routinemanager.support.TestUser;

/**
 * Flow: a user builds their weekday template (default routine) and edits its tasks.
 * Overlap-shift variations are in unit/TaskTimeFunctionsTest; colour rules in
 * unit/TaskColorServiceTest. Access to another user's routine is not asserted:
 * these endpoints have no ownership check yet (bug filed by F008, research R6).
 */
class DefaultRoutineComponentTest extends BaseComponentTest {

    private static final String BASE = "/api/v1/default-routine";

    @Test
    void newUserHasAnEmptyWeekdayRoutine() throws Exception {
        TestUser user = newUser("default");
        JsonNode routine = weekday(user);
        assertEquals("Weekday Routine", routine.get("name").asText());
        assertEquals("DAILY", routine.get("routineType").asText());
        assertEquals(0, routine.get("tasks").size());
    }

    @Test
    void addedTasksAreStoredWithComputedEndTimes() throws Exception {
        TestUser user = newUser("default");
        String routineId = weekday(user).get("id").asText();

        JsonNode created = read(mvc.perform(as(user, post(BASE + "/" + routineId + "/tasks"), List.of(
                        task("Morning Stretch", "07:00", 15), task("Plan Day", "08:00", 30))))
                .andExpect(status().isOk()));
        assertEquals(2, created.size());
        assertEquals("Morning Stretch", created.get(0).get("name").asText());
        assertEquals("07:15", created.get(0).get("endTime").asText());
        assertEquals("08:30", created.get(1).get("endTime").asText());

        JsonNode tasks = weekday(user).get("tasks");
        assertEquals(2, tasks.size());
    }

    @Test
    void insertingAnOverlappingTaskMovesTheExistingOneLater() throws Exception {
        TestUser user = newUser("default");
        String routineId = weekday(user).get("id").asText();
        addTasks(user, routineId, List.of(task("Existing", "09:00", 60)));

        addTasks(user, routineId, List.of(task("Inserted", "09:30", 30)));

        JsonNode existing = byName(weekday(user).get("tasks"), "Existing");
        assertEquals("10:00", existing.get("startTime").asText());
        assertEquals(60, existing.get("durationMinutes").asInt());
    }

    @Test
    void tasksCanBeRenamedAndDeleted() throws Exception {
        TestUser user = newUser("default");
        String routineId = weekday(user).get("id").asText();
        JsonNode created = addTasks(user, routineId, List.of(task("A", "09:00", 30), task("B", "10:00", 30)));
        String a = created.get(0).get("id").asText();
        String b = created.get(1).get("id").asText();

        mvc.perform(as(user, put(BASE + "/tasks"), List.of(Map.of(
                        "id", a, "name", "A renamed", "startTime", "09:00", "durationMinutes", 30))))
                .andExpect(status().isOk());
        assertEquals("A renamed", byName(weekday(user).get("tasks"), "A renamed").get("name").asText());

        mvc.perform(as(user, delete(BASE + "/tasks").param("taskIds", a, b))).andExpect(status().isNoContent());
        assertEquals(0, weekday(user).get("tasks").size());
    }

    @Test
    void replacingTheRoutineChangesNameAndTasks() throws Exception {
        TestUser user = newUser("default");
        String routineId = weekday(user).get("id").asText();
        addTasks(user, routineId, List.of(task("Old", "09:00", 30)));

        mvc.perform(as(user, put(BASE + "/" + routineId), Map.of(
                        "name", "Updated Name", "tasks", List.of(task("New Task", "07:00", 20)))))
                .andExpect(status().isOk());

        JsonNode routine = weekday(user);
        assertEquals("Updated Name", routine.get("name").asText());
        assertEquals(1, routine.get("tasks").size());
        assertEquals("New Task", routine.get("tasks").get(0).get("name").asText());
    }

    @Test
    void invalidTaskIsRefused() throws Exception {
        TestUser user = newUser("default");
        String routineId = weekday(user).get("id").asText();
        mvc.perform(as(user, post(BASE + "/" + routineId + "/tasks"), List.of(Map.of(
                        "name", "Bad", "startTime", "06:00", "durationMinutes", 30, "color", "gold"))))
                .andExpect(error(HttpStatus.BAD_REQUEST));
        assertEquals(0, weekday(user).get("tasks").size());
    }

    @Test
    void requestWithoutTokenIsNotAuthenticated() throws Exception {
        TestUser user = newUser("default");
        mvc.perform(get(BASE + "/users/" + user.id())).andExpect(error(HttpStatus.UNAUTHORIZED));
    }

    // ---------- helpers ----------

    private JsonNode weekday(TestUser user) throws Exception {
        return read(mvc.perform(as(user, get(BASE + "/users/" + user.id()))).andExpect(status().isOk()));
    }

    private JsonNode addTasks(TestUser user, String routineId, List<Map<String, Object>> tasks) throws Exception {
        return read(mvc.perform(as(user, post(BASE + "/" + routineId + "/tasks"), tasks)).andExpect(status().isOk()));
    }

    private static Map<String, Object> task(String name, String start, int minutes) {
        return Map.of("name", name, "startTime", start, "durationMinutes", minutes);
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
