package com.tom.payment.routinemanager.funcs;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalTime;
import java.util.Collection;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.ToIntFunction;

/**
 * Task time rules on a 24h cycle: a task may run past midnight (F011). Overlaps are never moved; they are capped (T002).
 */
public final class TaskTimeFunctions {

    public static final int MAX_PARALLEL_TASKS = 3;
    public static final String OVERLAP_CAP_MESSAGE = "A time slot can hold at most 3 tasks.";

    private static final int MINUTES_PER_DAY = 24 * 60;

    private TaskTimeFunctions() {
    }

    /** A task lasts 1 minute to 24 hours (F011, research R3); otherwise 400. */
    public static void requireValidDuration(int durationMinutes) {
        if (durationMinutes < 1 || durationMinutes > MINUTES_PER_DAY) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "durationMinutes must be between 1 and 1440");
        }
    }

    /**
     * At most {@link #MAX_PARALLEL_TASKS} tasks may cover any one minute of the 24h cycle (T002, research R4).
     * Only minutes covered by a changed task are checked, so an old crowded spot elsewhere never fails a save.
     */
    public static <T> void requireOverlapCap(Collection<T> allTasks, Collection<T> changedTasks,
            Function<T, LocalTime> start, ToIntFunction<T> durationMinutes) {
        if (changedTasks.isEmpty()) {
            return;
        }
        int[] counts = new int[MINUTES_PER_DAY];
        for (T task : allTasks) {
            forEachMinute(task, start, durationMinutes, minute -> counts[minute]++);
        }
        for (T task : changedTasks) {
            forEachMinute(task, start, durationMinutes, minute -> {
                if (counts[minute] > MAX_PARALLEL_TASKS) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, OVERLAP_CAP_MESSAGE);
                }
            });
        }
    }

    private static <T> void forEachMinute(T task, Function<T, LocalTime> start, ToIntFunction<T> durationMinutes,
            IntConsumer action) {
        LocalTime startTime = start.apply(task);
        if (startTime == null) {
            return;
        }
        int first = startTime.toSecondOfDay() / 60;
        int length = Math.min(durationMinutes.applyAsInt(task), MINUTES_PER_DAY);
        for (int i = 0; i < length; i++) {
            action.accept((first + i) % MINUTES_PER_DAY);
        }
    }
}
