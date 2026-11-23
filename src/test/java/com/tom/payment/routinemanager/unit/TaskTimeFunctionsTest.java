package com.tom.payment.routinemanager.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.tom.payment.routinemanager.funcs.TaskTimeFunctions;
import com.tom.payment.routinemanager.model.DailyTask;
import com.tom.payment.routinemanager.model.RoutineTaskTemplate;

/**
 * Rule: inserting a task pushes overlapping later tasks to start when it ends,
 * keeping their duration; a pushed task can push the next one in turn.
 */
class TaskTimeFunctionsTest {

    // ---------- default routine tasks ----------

    @Test
    void overlappingTemplateTaskMovesToTheEndOfTheNewOne() {
        RoutineTaskTemplate existing = template("Existing", "09:00", 60);
        RoutineTaskTemplate inserted = template("Inserted", "09:30", 30);
        TaskTimeFunctions.standardiseTasks(new ArrayList<>(List.of(existing)), inserted);
        assertEquals(LocalTime.of(10, 0), existing.getStartTime());
        assertEquals(60, existing.getDurationMinutes());
    }

    @Test
    void nonOverlappingTemplateTaskIsUntouched() {
        RoutineTaskTemplate earlier = template("Earlier", "07:00", 30);
        RoutineTaskTemplate later = template("Later", "11:00", 30);
        TaskTimeFunctions.standardiseTasks(new ArrayList<>(List.of(earlier, later)), template("New", "09:00", 60));
        assertEquals(LocalTime.of(7, 0), earlier.getStartTime());
        assertEquals(LocalTime.of(11, 0), later.getStartTime());
    }

    @Test
    void touchingEndToStartIsNotAnOverlap() {
        RoutineTaskTemplate next = template("Next", "10:00", 30);
        TaskTimeFunctions.standardiseTasks(new ArrayList<>(List.of(next)), template("New", "09:00", 60));
        assertEquals(LocalTime.of(10, 0), next.getStartTime());
    }

    @Test
    void pushedTemplateTasksPushTheNextOneInTurn() {
        RoutineTaskTemplate first = template("First", "09:00", 30);
        RoutineTaskTemplate second = template("Second", "09:30", 30);
        TaskTimeFunctions.standardiseTasks(new ArrayList<>(List.of(second, first)), template("New", "09:00", 60));
        assertEquals(LocalTime.of(10, 0), first.getStartTime());
        assertEquals(LocalTime.of(10, 30), second.getStartTime());
    }

    // ---------- daily tasks ----------

    @Test
    void overlappingDailyTaskMovesToTheEndOfTheNewOne() {
        DailyTask existing = daily("Existing", "09:00", 60);
        TaskTimeFunctions.shiftOverlappingTasks(new ArrayList<>(List.of(existing)), daily("Inserted", "09:30", 30));
        assertEquals(LocalTime.of(10, 0), existing.getStartTime());
        assertEquals(60, existing.getDurationMinutes());
    }

    @Test
    void nonOverlappingDailyTaskIsUntouched() {
        DailyTask later = daily("Later", "11:00", 30);
        TaskTimeFunctions.shiftOverlappingTasks(new ArrayList<>(List.of(later)), daily("New", "09:00", 60));
        assertEquals(LocalTime.of(11, 0), later.getStartTime());
    }

    @Test
    void pushedDailyTasksPushTheNextOneInTurn() {
        DailyTask first = daily("First", "09:00", 30);
        DailyTask second = daily("Second", "09:30", 30);
        TaskTimeFunctions.shiftOverlappingTasks(new ArrayList<>(List.of(first, second)), daily("New", "09:00", 60));
        assertEquals(LocalTime.of(10, 0), first.getStartTime());
        assertEquals(LocalTime.of(10, 30), second.getStartTime());
    }

    @Test
    void invalidNewTaskChangesNothing() {
        DailyTask existing = daily("Existing", "09:00", 60);
        TaskTimeFunctions.shiftOverlappingTasks(new ArrayList<>(List.of(existing)), daily("Zero", "09:30", 0));
        TaskTimeFunctions.shiftOverlappingTasks(new ArrayList<>(List.of(existing)), null);
        assertEquals(LocalTime.of(9, 0), existing.getStartTime());
    }

    private static RoutineTaskTemplate template(String name, String start, int minutes) {
        RoutineTaskTemplate task = new RoutineTaskTemplate();
        task.setName(name);
        task.setStartTime(LocalTime.parse(start));
        task.setDurationMinutes(minutes);
        return task;
    }

    private static DailyTask daily(String name, String start, int minutes) {
        DailyTask task = new DailyTask();
        task.setName(name);
        task.setStartTime(LocalTime.parse(start));
        task.setDurationMinutes(minutes);
        return task;
    }
}
