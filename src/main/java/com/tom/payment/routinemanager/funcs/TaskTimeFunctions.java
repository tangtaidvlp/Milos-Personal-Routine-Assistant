package com.tom.payment.routinemanager.funcs;

import com.tom.payment.routinemanager.model.DailyTask;
import com.tom.payment.routinemanager.model.RoutineTaskTemplate;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Overlap push on a 24h cycle: a task may run past midnight (F011, research R2).
 * An overlapping sibling moves to start at the new task's end; a pushed task can push the next one.
 */
public final class TaskTimeFunctions {

    private static final int MINUTES_PER_DAY = 24 * 60;

    private TaskTimeFunctions() {
    }

    /** A task lasts 1 minute to 24 hours (F011, research R3); otherwise 400. */
    public static void requireValidDuration(int durationMinutes) {
        if (durationMinutes < 1 || durationMinutes > MINUTES_PER_DAY) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "durationMinutes must be between 1 and 1440");
        }
    }

    public static void standardiseTasks(List<RoutineTaskTemplate> existingTasks, RoutineTaskTemplate newTask) {
        if (newTask == null) {
            return;
        }
        shift(existingTasks, newTask, newTask.getDurationMinutes(),
                RoutineTaskTemplate::getStartTime, RoutineTaskTemplate::getEndTime, RoutineTaskTemplate::setStartTime);
    }

    public static void shiftOverlappingTasks(List<DailyTask> existingTasks, DailyTask newTask) {
        if (newTask == null) {
            return;
        }
        shift(existingTasks, newTask, newTask.getDurationMinutes(),
                DailyTask::getStartTime, DailyTask::getEndTime, DailyTask::setStartTime);
    }

    private static <T> void shift(List<T> existingTasks, T newTask, int newDuration,
            Function<T, LocalTime> start, Function<T, LocalTime> end, BiConsumer<T, LocalTime> setStart) {
        if (existingTasks == null || start.apply(newTask) == null || newDuration <= 0) {
            return;
        }

        LocalTime insertionStart = start.apply(newTask);
        LocalTime insertionEnd = end.apply(newTask);
        if (insertionEnd == null) {
            return;
        }

        List<T> sortedTasks = new ArrayList<>(existingTasks);
        sortedTasks.sort(Comparator.comparing(start, Comparator.nullsLast(Comparator.naturalOrder())));

        for (T task : sortedTasks) {
            if (task == newTask || start.apply(task) == null || end.apply(task) == null) {
                continue;
            }
            if (isOverlapping(minutes(start.apply(task)), minutes(end.apply(task)),
                    minutes(insertionStart), minutes(insertionEnd))) {
                setStart.accept(task, insertionEnd);
                insertionEnd = end.apply(task);
            }
        }
    }

    /** Ranges on a 24h cycle: {@code end <= start} wraps past midnight, {@code end == start} is a full day. */
    private static boolean isOverlapping(int firstStart, int firstEnd, int secondStart, int secondEnd) {
        return cycle(secondStart - firstStart) < length(firstStart, firstEnd)
                || cycle(firstStart - secondStart) < length(secondStart, secondEnd);
    }

    private static int length(int start, int end) {
        int length = cycle(end - start);
        return length == 0 ? MINUTES_PER_DAY : length;
    }

    private static int cycle(int minutes) {
        return Math.floorMod(minutes, MINUTES_PER_DAY);
    }

    private static int minutes(LocalTime time) {
        return time.toSecondOfDay() / 60;
    }
}
