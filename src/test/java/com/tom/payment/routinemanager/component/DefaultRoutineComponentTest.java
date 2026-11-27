package com.tom.payment.routinemanager.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
 * Overlap-cap variations are in unit/TaskTimeFunctionsTest; colour rules in
 * unit/TaskColorServiceTest. Access to another user's routine is not asserted, except
 * for Copy Weekday to Weekend (F012): the older endpoints have no ownership check yet
 * (bug filed by F008, research R6).
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
    void overlappingTasksKeepTheirTimes() throws Exception {
        TestUser user = newUser("default");
        String routineId = weekday(user).get("id").asText();
        addTasks(user, routineId, List.of(task("Existing", "09:00", 60)));

        addTasks(user, routineId, List.of(task("Inserted", "09:30", 30)));

        JsonNode tasks = weekday(user).get("tasks");
        assertEquals("09:00", byName(tasks, "Existing").get("startTime").asText());
        assertEquals("09:30", byName(tasks, "Inserted").get("startTime").asText());
    }

    @Test
    void aFourthTaskAtTheSameMomentIsRefusedAndNothingIsStored() throws Exception {
        TestUser user = newUser("default");
        String routineId = weekday(user).get("id").asText();
        addTasks(user, routineId, List.of(task("A", "09:00", 60), task("B", "09:00", 60), task("C", "09:00", 60)));

        JsonNode refusal = read(mvc.perform(as(user, post(BASE + "/" + routineId + "/tasks"),
                        List.of(task("D", "09:30", 15))))
                .andExpect(error(HttpStatus.CONFLICT)));
        assertEquals("A time slot can hold at most 3 tasks.", refusal.get("message").asText());
        assertEquals(3, weekday(user).get("tasks").size());
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
    void anOvernightTaskIsStoredAsOneTask() throws Exception {
        TestUser user = newUser("default");
        String routineId = weekday(user).get("id").asText();

        JsonNode created = addTasks(user, routineId, List.of(task("Sleep", "22:00", 480)));
        assertEquals("06:00", created.get(0).get("endTime").asText());
        assertEquals(1, weekday(user).get("tasks").size());

        mvc.perform(as(user, put(BASE + "/tasks"), List.of(Map.of(
                        "id", created.get(0).get("id").asText(), "name", "Sleep", "startTime", "23:00",
                        "durationMinutes", 480))))
                .andExpect(status().isOk());
        JsonNode tasks = weekday(user).get("tasks");
        assertEquals(1, tasks.size());
        assertEquals("07:00", tasks.get(0).get("endTime").asText());
    }

    @Test
    void durationOutsideOneDayIsRefused() throws Exception {
        TestUser user = newUser("default");
        String routineId = weekday(user).get("id").asText();
        for (int minutes : new int[] {1441, 0}) {
            JsonNode refusal = read(mvc.perform(as(user, post(BASE + "/" + routineId + "/tasks"),
                            List.of(task("Bad", "22:00", minutes))))
                    .andExpect(error(HttpStatus.BAD_REQUEST)));
            assertEquals("durationMinutes must be between 1 and 1440", refusal.get("message").asText());
        }
        assertEquals(0, weekday(user).get("tasks").size());
    }

    @Test
    void requestWithoutTokenIsNotAuthenticated() throws Exception {
        TestUser user = newUser("default");
        mvc.perform(get(BASE + "/users/" + user.id())).andExpect(error(HttpStatus.UNAUTHORIZED));
    }

    // ---------- Copy Weekday to Weekend (specs/F012) ----------

    @Test
    void copyReplacesTheWeekendTasksWithCopiesOfTheWeekdayOnes() throws Exception {
        TestUser user = newUser("copy");
        addTasks(user, weekday(user).get("id").asText(), List.of(
                Map.of("name", "Work", "description", "Office", "startTime", "09:00", "durationMinutes", 240),
                task("Gym", "18:00", 60)));
        addTasks(user, weekend(user).get("id").asText(), List.of(task("Hike", "08:00", 180)));

        mvc.perform(as(user, post(copyPath(user)))).andExpect(status().isNoContent());

        JsonNode weekdayTasks = weekday(user).get("tasks");
        JsonNode weekendTasks = weekend(user).get("tasks");
        assertEquals(2, weekdayTasks.size());
        assertEquals(2, weekendTasks.size());
        for (JsonNode source : weekdayTasks) {
            JsonNode copy = byName(weekendTasks, source.get("name").asText());
            assertEquals(source.get("description").asText(), copy.get("description").asText());
            assertEquals(source.get("startTime").asText(), copy.get("startTime").asText());
            assertEquals(source.get("durationMinutes").asInt(), copy.get("durationMinutes").asInt());
            assertEquals(source.get("color").asText(), copy.get("color").asText());
            assertNotEquals(source.get("id").asText(), copy.get("id").asText());
        }
    }

    @Test
    void copyingAnEmptyWeekdayRoutineEmptiesTheWeekend() throws Exception {
        TestUser user = newUser("copy");
        addTasks(user, weekend(user).get("id").asText(), List.of(task("Hike", "08:00", 180)));

        mvc.perform(as(user, post(copyPath(user)))).andExpect(status().isNoContent());

        assertEquals(0, weekend(user).get("tasks").size());
    }

    @Test
    void anotherUsersRoutineCannotBeCopied() throws Exception {
        TestUser owner = newUser("copy");
        TestUser other = newUser("copy");
        mvc.perform(as(other, post(copyPath(owner)))).andExpect(error(HttpStatus.FORBIDDEN));
    }

    // ---------- Overlay (specs/F017) ----------

    @Test
    void overlayDefaultsToFalseIsStoredAndKeptWhenNotSent() throws Exception {
        TestUser user = newUser("overlay");
        String routineId = weekday(user).get("id").asText();

        JsonNode created = addTasks(user, routineId, List.of(task("Plain", "07:00", 30),
                Map.of("name", "Free", "startTime", "08:00", "durationMinutes", 30, "overlay", true)));
        assertEquals(false, created.get(0).get("overlay").asBoolean());
        assertEquals(true, created.get(1).get("overlay").asBoolean());
        String free = created.get(1).get("id").asText();

        mvc.perform(as(user, put(BASE + "/tasks"), List.of(Map.of(
                        "id", free, "name", "Free", "startTime", "08:00", "durationMinutes", 30))))
                .andExpect(status().isOk());
        assertEquals(true, byName(weekday(user).get("tasks"), "Free").get("overlay").asBoolean());

        mvc.perform(as(user, put(BASE + "/tasks"), List.of(Map.of(
                        "id", free, "name", "Free", "startTime", "08:00", "durationMinutes", 30, "overlay", false))))
                .andExpect(status().isOk());
        assertEquals(false, byName(weekday(user).get("tasks"), "Free").get("overlay").asBoolean());
    }

    @Test
    void copyKeepsOverlay() throws Exception {
        TestUser user = newUser("overlay");
        addTasks(user, weekday(user).get("id").asText(), List.of(
                Map.of("name", "Free", "startTime", "08:00", "durationMinutes", 30, "overlay", true),
                task("Plain", "09:00", 30)));

        mvc.perform(as(user, post(copyPath(user)))).andExpect(status().isNoContent());

        JsonNode weekendTasks = weekend(user).get("tasks");
        assertEquals(true, byName(weekendTasks, "Free").get("overlay").asBoolean());
        assertEquals(false, byName(weekendTasks, "Plain").get("overlay").asBoolean());
    }

    // ---------- helpers ----------

    private static String copyPath(TestUser user) {
        return BASE + "/users/" + user.id() + "/copy-weekday-to-weekend";
    }

    private JsonNode weekend(TestUser user) throws Exception {
        return read(mvc.perform(as(user, get(BASE + "/users/" + user.id()).param("type", "WEEKEND")))
                .andExpect(status().isOk()));
    }

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
