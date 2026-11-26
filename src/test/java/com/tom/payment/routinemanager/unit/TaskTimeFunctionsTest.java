package com.tom.payment.routinemanager.unit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.tom.payment.routinemanager.funcs.TaskTimeFunctions;
import com.tom.payment.routinemanager.model.DailyTask;

/**
 * Rules: a task lasts 1 minute to 24 hours; at most 3 tasks may cover one minute of the 24h cycle,
 * checked only for the changed tasks (T002). A task may run past midnight (F011).
 */
class TaskTimeFunctionsTest {

    @Test
    void durationOutsideOneMinuteToOneDayIsRefused() {
        assertDoesNotThrow(() -> TaskTimeFunctions.requireValidDuration(1));
        assertDoesNotThrow(() -> TaskTimeFunctions.requireValidDuration(1440));
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> TaskTimeFunctions.requireValidDuration(0)).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> TaskTimeFunctions.requireValidDuration(1441)).getStatusCode());
    }

    @Test
    void threeTasksAtOnceAreAllowed() {
        List<DailyTask> all = List.of(daily("09:00", 60), daily("09:00", 60), daily("09:30", 60));
        assertDoesNotThrow(() -> check(all, all));
    }

    @Test
    void aFourthTaskAtTheSameMomentIsRefused() {
        List<DailyTask> all = List.of(daily("09:00", 60), daily("09:00", 60), daily("09:00", 60), daily("09:30", 15));
        ResponseStatusException refusal = assertThrows(ResponseStatusException.class,
                () -> check(all, List.of(all.get(3))));
        assertEquals(HttpStatus.CONFLICT, refusal.getStatusCode());
        assertEquals(TaskTimeFunctions.OVERLAP_CAP_MESSAGE, refusal.getReason());
    }

    @Test
    void touchingEndsDoNotOverlap() {
        List<DailyTask> all = List.of(daily("10:00", 60), daily("10:00", 60), daily("10:00", 60), daily("11:00", 60));
        assertDoesNotThrow(() -> check(all, all));
    }

    @Test
    void anOvernightTaskCountsBeforeAndAfterMidnight() {
        DailyTask sleep = daily("22:00", 480);
        List<DailyTask> evening = List.of(sleep, daily("23:00", 30), daily("23:00", 30), daily("23:10", 10));
        assertThrows(ResponseStatusException.class, () -> check(evening, List.of(sleep)));

        List<DailyTask> morning = List.of(sleep, daily("05:00", 30), daily("05:00", 30), daily("05:10", 10));
        assertThrows(ResponseStatusException.class, () -> check(morning, List.of(sleep)));
    }

    @Test
    void aLongTaskOverThreeShortTasksAtDifferentTimesIsAllowed() {
        List<DailyTask> all = List.of(daily("08:00", 240), daily("08:00", 60), daily("09:00", 60), daily("10:00", 60));
        assertDoesNotThrow(() -> check(all, all));
    }

    @Test
    void anUnchangedCrowdedClusterElsewhereDoesNotFailAChangedTask() {
        DailyTask changed = daily("15:00", 30);
        List<DailyTask> all = new ArrayList<>(List.of(daily("09:00", 60), daily("09:00", 60), daily("09:00", 60),
                daily("09:00", 60), changed));
        assertDoesNotThrow(() -> check(all, List.of(changed)));
    }

    @Test
    void aFullDayTaskCoversEveryMinute() {
        DailyTask fullDay = daily("00:00", 1440);
        List<DailyTask> all = List.of(daily("03:00", 10), daily("03:00", 10), daily("03:00", 10), fullDay);
        assertThrows(ResponseStatusException.class, () -> check(all, List.of(fullDay)));
        List<DailyTask> late = List.of(daily("23:59", 1), daily("23:59", 1), daily("23:59", 1), fullDay);
        assertThrows(ResponseStatusException.class, () -> check(late, List.of(fullDay)));
    }

    private static void check(List<DailyTask> all, List<DailyTask> changed) {
        TaskTimeFunctions.requireOverlapCap(all, changed, DailyTask::getStartTime, DailyTask::getDurationMinutes);
    }

    private static DailyTask daily(String start, int minutes) {
        DailyTask task = new DailyTask();
        task.setName("T");
        task.setStartTime(LocalTime.parse(start));
        task.setDurationMinutes(minutes);
        return task;
    }
}
