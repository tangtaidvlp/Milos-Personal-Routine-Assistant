package com.tom.payment.routinemanager.funcs;

import com.tom.payment.routinemanager.model.DailyTask;
import com.tom.payment.routinemanager.model.RoutineTaskTemplate;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class TaskTimeFunctions {

    private TaskTimeFunctions() {
    }

    public static void standardiseTasks(List<RoutineTaskTemplate> existingTasks, RoutineTaskTemplate newTask) {
        if (existingTasks == null || newTask == null || newTask.getStartTime() == null || newTask.getDurationMinutes() <= 0) {
            return;
        }

        LocalTime insertionStart = newTask.getStartTime();
        LocalTime insertionEnd = newTask.getEndTime();

        if (insertionEnd == null) {
            return;
        }

        List<RoutineTaskTemplate> sortedTasks = new ArrayList<>(existingTasks);
        sortedTasks.sort(Comparator.comparing(RoutineTaskTemplate::getStartTime, Comparator.nullsLast(Comparator.naturalOrder())));

        for (RoutineTaskTemplate task : sortedTasks) {
            if (task == newTask || task.getStartTime() == null) {
                continue;
            }

            LocalTime taskStart = task.getStartTime();
            LocalTime taskEnd = task.getEndTime();
            if (taskEnd == null) {
                continue;
            }

            if (isOverlapping(taskStart, taskEnd, insertionStart, insertionEnd)) {
                task.setStartTime(insertionEnd);
                insertionEnd = task.getEndTime();
            }
        }
    }

    public static void shiftOverlappingTasks(List<DailyTask> existingTasks, DailyTask newTask) {
        if (existingTasks == null || newTask == null || newTask.getStartTime() == null || newTask.getDurationMinutes() <= 0) {
            return;
        }

        LocalTime insertionStart = newTask.getStartTime();
        LocalTime insertionEnd = newTask.getEndTime();

        if (insertionEnd == null) {
            return;
        }

        List<DailyTask> sortedTasks = new ArrayList<>(existingTasks);
        sortedTasks.sort(Comparator.comparing(DailyTask::getStartTime, Comparator.nullsLast(Comparator.naturalOrder())));

        for (DailyTask task : sortedTasks) {
            if (task == newTask || task.getStartTime() == null) {
                continue;
            }

            LocalTime taskStart = task.getStartTime();
            LocalTime taskEnd = task.getEndTime();
            if (taskEnd == null) {
                continue;
            }

            if (isOverlapping(taskStart, taskEnd, insertionStart, insertionEnd)) {
                task.setStartTime(insertionEnd);
                insertionEnd = task.getEndTime();
            }
        }
    }

    private static boolean isOverlapping(LocalTime firstStart, LocalTime firstEnd, LocalTime secondStart, LocalTime secondEnd) {
        return firstStart.isBefore(secondEnd) && secondStart.isBefore(firstEnd);
    }
}
