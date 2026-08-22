package com.tom.payment.routinemanager.service.aitools;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import com.tom.payment.routinemanager.model.DailyRoutine;
import com.tom.payment.routinemanager.model.DailyTask;
import com.tom.payment.routinemanager.model.User;
import com.tom.payment.routinemanager.repository.DailyRoutineRepository;
import com.tom.payment.routinemanager.service.DailyRoutineService;
import com.tom.payment.routinemanager.service.UserService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DailyRoutineAiTools {

    private final DailyRoutineService dailyRoutineService;
    private final UserService userService;
    private final DailyRoutineRepository dailyRoutineRepository;

    public record DailyTaskInput(String name, String description, String startTime, int durationMinutes, boolean completed) {}
    public record DailyTaskUpdateInput(String taskId, String newName, String description, String startTime, Integer durationMinutes, Boolean completed) {}

    @Tool(description = "Add one or more tasks to the user's daily routine for a specific date. " +
            "The date must be in yyyy-MM-dd format. Each task needs name, description, startTime (HH:mm), durationMinutes, and completed status.")
    public String addDailyTasks(
            @ToolParam(description = "The UUID of the user") String userId,
            @ToolParam(description = "The date in yyyy-MM-dd format") String date,
            @ToolParam(description = "List of daily tasks to add") List<DailyTaskInput> tasks) {
        try {
            User user = userService.getUserById(UUID.fromString(userId));
            LocalDate targetDate = LocalDate.parse(date);
            if (targetDate.isBefore(LocalDate.now())) {
                return "Cannot add new scheduled tasks to a past daily routine (" + date + ") — that day has already passed.";
            }
            DailyRoutine routine = dailyRoutineRepository.findByUserAndDate(user, targetDate)
                    .orElseThrow(() -> new RuntimeException("Daily routine not found for date: " + date));

            List<DailyTask> entities = tasks.stream().map(input -> {
                DailyTask t = new DailyTask();
                t.setName(input.name());
                t.setDescription(input.description());
                t.setStartTime(LocalTime.parse(input.startTime()));
                t.setDurationMinutes(input.durationMinutes());
                t.setCompleted(input.completed());
                return t;
            }).collect(Collectors.toList());

            dailyRoutineService.addDailyTasks(routine.getId(), entities);
            return "Successfully added " + entities.size() + " task(s) to daily routine for " + date + ".";
        } catch (Exception e) {
            return "Failed to add daily tasks: " + e.getMessage();
        }
    }

    @Tool(description = "Update one or more tasks in the user's daily routine for a specific date. " +
            "Identify each task by its taskId. Can change name, time, duration, or completed status.")
    public String updateDailyTasks(
            @ToolParam(description = "The UUID of the user") String userId,
            @ToolParam(description = "The date in yyyy-MM-dd format") String date,
            @ToolParam(description = "List of daily task updates") List<DailyTaskUpdateInput> updates) {
        try {
            User user = userService.getUserById(UUID.fromString(userId));
            LocalDate targetDate = LocalDate.parse(date);
            boolean isPastDate = targetDate.isBefore(LocalDate.now());
            DailyRoutine routine = dailyRoutineRepository.findByUserAndDate(user, targetDate)
                    .orElseThrow(() -> new RuntimeException("Daily routine not found for date: " + date));

            List<DailyTask> toUpdate = new ArrayList<>();
            for (DailyTaskUpdateInput u : updates) {
                UUID taskId = UUID.fromString(u.taskId());
                DailyTask existing = routine.getTasks().stream()
                        .filter(t -> t.getId().equals(taskId))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("Task id '" + u.taskId() + "' not found in daily routine for " + date));

                DailyTask details = new DailyTask();
                details.setId(existing.getId());
                details.setName(u.newName() != null ? u.newName() : existing.getName());
                details.setDescription(u.description() != null ? u.description() : existing.getDescription());
                // Past daily routines are frozen: never change the time schedule, only content/completion.
                details.setStartTime(!isPastDate && u.startTime() != null ? LocalTime.parse(u.startTime()) : existing.getStartTime());
                Integer requestedDuration = u.durationMinutes();
                details.setDurationMinutes(!isPastDate && requestedDuration != null ? requestedDuration : existing.getDurationMinutes());
                Boolean requestedCompleted = u.completed();
                details.setCompleted(requestedCompleted != null ? requestedCompleted : existing.isCompleted());
                toUpdate.add(details);
            }

            dailyRoutineService.updateDailyTasks(toUpdate);
            return "Successfully updated " + toUpdate.size() + " task(s) in daily routine for " + date + ".";
        } catch (Exception e) {
            return "Failed to update daily tasks: " + e.getMessage();
        }
    }

    @Tool(description = "Delete one or more tasks from the user's daily routine for a specific date by their task IDs.")
    public String deleteDailyTasks(
            @ToolParam(description = "The UUID of the user") String userId,
            @ToolParam(description = "The date in yyyy-MM-dd format") String date,
            @ToolParam(description = "List of task IDs to delete") List<String> taskIds) {
        try {
            User user = userService.getUserById(UUID.fromString(userId));
            LocalDate targetDate = LocalDate.parse(date);
            DailyRoutine routine = dailyRoutineRepository.findByUserAndDate(user, targetDate)
                    .orElseThrow(() -> new RuntimeException("Daily routine not found for date: " + date));

            List<UUID> requestedIds = taskIds.stream()
                    .map(UUID::fromString)
                    .collect(Collectors.toList());

            List<UUID> ids = routine.getTasks().stream()
                    .filter(t -> requestedIds.contains(t.getId()))
                    .map(t -> t.getId())
                    .collect(Collectors.toList());

            if (ids.isEmpty()) return "No matching tasks found to delete.";

            dailyRoutineService.deleteDailyTasks(ids);
            return "Successfully deleted " + ids.size() + " task(s) from daily routine for " + date + ".";
        } catch (Exception e) {
            return "Failed to delete daily tasks: " + e.getMessage();
        }
    }
}
