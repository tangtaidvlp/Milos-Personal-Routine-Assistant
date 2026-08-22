package com.tom.payment.routinemanager.config;

import com.tom.payment.routinemanager.model.*;
import com.tom.payment.routinemanager.repository.DailyRoutineRepository;
import com.tom.payment.routinemanager.repository.DefaultRoutineRepository;
import com.tom.payment.routinemanager.service.DailyRoutineService;
import com.tom.payment.routinemanager.service.DefaultRoutineService;
import com.tom.payment.routinemanager.service.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Configuration
public class AiRoutineFunctions {

    // --- DTOs for Gemini Function Calling (Bulk Support) ---
    
    public record TaskTemplateInput(String name, String description, String startTime, int durationMinutes) {}
    public record AddDefaultTasksRequest(UUID userId, List<TaskTemplateInput> tasks) {}

    public record TaskTemplateUpdateInput(String taskName, String newName, String description, String startTime, Integer durationMinutes) {}
    public record UpdateDefaultTasksRequest(UUID userId, List<TaskTemplateUpdateInput> updates) {}

    public record DeleteDefaultTasksRequest(UUID userId, List<String> taskNames) {}

    public record DailyTaskInput(String name, String description, String startTime, int durationMinutes, boolean completed) {}
    public record AddDailyTasksRequest(UUID userId, String date, List<DailyTaskInput> tasks) {}

    public record DailyTaskUpdateInput(String taskName, String newName, String description, String startTime, Integer durationMinutes, Boolean completed) {}
    public record UpdateDailyTasksRequest(UUID userId, String date, List<DailyTaskUpdateInput> updates) {}

    public record DeleteDailyTasksRequest(UUID userId, String date, List<String> taskNames) {}

    public record ToolResponse(boolean success, String message) {}

    // --- Default Routine AI Functions ---

    @Bean
    @Description("Add a list of tasks to the user's default routine template.")
    public Function<AddDefaultTasksRequest, ToolResponse> addDefaultTasksFunction(
            DefaultRoutineService routineService, UserService userService, DefaultRoutineRepository defaultRoutineRepository) {
        return request -> {
            try {
                User user = userService.getUserById(request.userId());
                DefaultRoutine routine = defaultRoutineRepository.findByUser(user)
                        .orElseThrow(() -> new RuntimeException("Default routine not found for user"));

                List<RoutineTaskTemplate> tasks = request.tasks().stream().map(input -> {
                    RoutineTaskTemplate task = new RoutineTaskTemplate();
                    task.setName(input.name());
                    task.setDescription(input.description());
                    task.setStartTime(LocalTime.parse(input.startTime()));
                    task.setDurationMinutes(input.durationMinutes());
                    return task;
                }).collect(Collectors.toList());

                routineService.addDefaultTasks(routine.getId(), tasks);
                return new ToolResponse(true, "Successfully added " + tasks.size() + " task(s) to default routine template.");
            } catch (Exception e) {
                return new ToolResponse(false, "Failed to add tasks to default routine: " + e.getMessage());
            }
        };
    }

    @Bean
    @Description("Update details of multiple tasks in the user's default routine template.")
    public Function<UpdateDefaultTasksRequest, ToolResponse> updateDefaultTasksFunction(
            DefaultRoutineService routineService, UserService userService, DefaultRoutineRepository defaultRoutineRepository) {
        return request -> {
            try {
                User user = userService.getUserById(request.userId());
                DefaultRoutine routine = defaultRoutineRepository.findByUser(user)
                        .orElseThrow(() -> new RuntimeException("Default routine not found for user"));

                List<RoutineTaskTemplate> tasksToUpdate = new ArrayList<>();

                for (TaskTemplateUpdateInput update : request.updates()) {
                    RoutineTaskTemplate existing = routine.getTasks().stream()
                            .filter(t -> t.getName().equalsIgnoreCase(update.taskName()))
                            .findFirst()
                            .orElseThrow(() -> new RuntimeException("Task '" + update.taskName() + "' not found in default routine"));

                    RoutineTaskTemplate updatedDetails = new RoutineTaskTemplate();
                    updatedDetails.setId(existing.getId());
                    updatedDetails.setName(update.newName() != null ? update.newName() : existing.getName());
                    updatedDetails.setDescription(update.description() != null ? update.description() : existing.getDescription());
                    updatedDetails.setStartTime(update.startTime() != null ? LocalTime.parse(update.startTime()) : existing.getStartTime());
                    Integer requestedDuration = update.durationMinutes();
                    int durationMinutes = requestedDuration != null ? requestedDuration : existing.getDurationMinutes();
                    updatedDetails.setDurationMinutes(durationMinutes);
                    
                    tasksToUpdate.add(updatedDetails);
                }

                routineService.updateDefaultTasks(tasksToUpdate);
                return new ToolResponse(true, "Successfully updated " + tasksToUpdate.size() + " task(s) in default routine template.");
            } catch (Exception e) {
                return new ToolResponse(false, "Failed to update default routine tasks: " + e.getMessage());
            }
        };
    }

    @Bean
    @Description("Delete multiple tasks from the user's default routine template by their names.")
    public Function<DeleteDefaultTasksRequest, ToolResponse> deleteDefaultTasksFunction(
            DefaultRoutineService routineService, UserService userService, DefaultRoutineRepository defaultRoutineRepository) {
        return request -> {
            try {
                User user = userService.getUserById(request.userId());
                DefaultRoutine routine = defaultRoutineRepository.findByUser(user)
                        .orElseThrow(() -> new RuntimeException("Default routine not found for user"));

                List<UUID> idsToDelete = routine.getTasks().stream()
                        .filter(t -> request.taskNames().stream().anyMatch(name -> name.equalsIgnoreCase(t.getName())))
                    .map(t -> t.getId())
                        .collect(Collectors.toList());

                if (idsToDelete.isEmpty()) {
                    return new ToolResponse(false, "No matching tasks found to delete.");
                }

                routineService.deleteDefaultTasks(idsToDelete);
                return new ToolResponse(true, "Successfully deleted " + idsToDelete.size() + " task(s) from default routine template.");
            } catch (Exception e) {
                return new ToolResponse(false, "Failed to delete default routine tasks: " + e.getMessage());
            }
        };
    }

    // --- Daily Routine AI Functions ---

    @Bean
    @Description("Add multiple tasks to the user's daily routine for a specific date (yyyy-MM-dd).")
    public Function<AddDailyTasksRequest, ToolResponse> addDailyTasksFunction(
            DailyRoutineService dailyRoutineService, UserService userService, DailyRoutineRepository dailyRoutineRepository) {
        return request -> {
            try {
                User user = userService.getUserById(request.userId());
                LocalDate date = LocalDate.parse(request.date());
                DailyRoutine routine = dailyRoutineRepository.findByUserAndDate(user, date)
                        .orElseThrow(() -> new RuntimeException("Daily routine not found for date: " + request.date()));

                List<DailyTask> tasks = request.tasks().stream().map(input -> {
                    DailyTask task = new DailyTask();
                    task.setName(input.name());
                    task.setDescription(input.description());
                    task.setStartTime(LocalTime.parse(input.startTime()));
                    task.setDurationMinutes(input.durationMinutes());
                    task.setCompleted(input.completed());
                    return task;
                }).collect(Collectors.toList());

                dailyRoutineService.addDailyTasks(routine.getId(), tasks);
                return new ToolResponse(true, "Successfully added " + tasks.size() + " task(s) to daily routine for " + request.date() + ".");
            } catch (Exception e) {
                return new ToolResponse(false, "Failed to add tasks to daily routine: " + e.getMessage());
            }
        };
    }

    @Bean
    @Description("Update multiple tasks in the user's daily routine for a specific date (yyyy-MM-dd), such as marking completed/incomplete or shifting times.")
    public Function<UpdateDailyTasksRequest, ToolResponse> updateDailyTasksFunction(
            DailyRoutineService dailyRoutineService, UserService userService, DailyRoutineRepository dailyRoutineRepository) {
        return request -> {
            try {
                User user = userService.getUserById(request.userId());
                LocalDate date = LocalDate.parse(request.date());
                DailyRoutine routine = dailyRoutineRepository.findByUserAndDate(user, date)
                        .orElseThrow(() -> new RuntimeException("Daily routine not found for date: " + request.date()));

                List<DailyTask> tasksToUpdate = new ArrayList<>();

                for (DailyTaskUpdateInput update : request.updates()) {
                    DailyTask existing = routine.getTasks().stream()
                            .filter(t -> t.getName().equalsIgnoreCase(update.taskName()))
                            .findFirst()
                            .orElseThrow(() -> new RuntimeException("Task '" + update.taskName() + "' not found in daily routine for " + request.date()));

                    DailyTask updatedDetails = new DailyTask();
                    updatedDetails.setId(existing.getId());
                    updatedDetails.setName(update.newName() != null ? update.newName() : existing.getName());
                    updatedDetails.setDescription(update.description() != null ? update.description() : existing.getDescription());
                    updatedDetails.setStartTime(update.startTime() != null ? LocalTime.parse(update.startTime()) : existing.getStartTime());
                    Integer requestedDuration = update.durationMinutes();
                    int durationMinutes = requestedDuration != null ? requestedDuration : existing.getDurationMinutes();
                    updatedDetails.setDurationMinutes(durationMinutes);
                    Boolean requestedCompleted = update.completed();
                    updatedDetails.setCompleted(requestedCompleted != null ? requestedCompleted : existing.isCompleted());
                    
                    tasksToUpdate.add(updatedDetails);
                }

                dailyRoutineService.updateDailyTasks(tasksToUpdate);
                return new ToolResponse(true, "Successfully updated " + tasksToUpdate.size() + " task(s) in daily routine for " + request.date() + ".");
            } catch (Exception e) {
                return new ToolResponse(false, "Failed to update daily routine tasks: " + e.getMessage());
            }
        };
    }

    @Bean
    @Description("Delete multiple tasks from the user's daily routine for a specific date (yyyy-MM-dd) by their names.")
    public Function<DeleteDailyTasksRequest, ToolResponse> deleteDailyTasksFunction(
            DailyRoutineService dailyRoutineService, UserService userService, DailyRoutineRepository dailyRoutineRepository) {
        return request -> {
            try {
                User user = userService.getUserById(request.userId());
                LocalDate date = LocalDate.parse(request.date());
                DailyRoutine routine = dailyRoutineRepository.findByUserAndDate(user, date)
                        .orElseThrow(() -> new RuntimeException("Daily routine not found for date: " + request.date()));

                List<UUID> idsToDelete = routine.getTasks().stream()
                        .filter(t -> request.taskNames().stream().anyMatch(name -> name.equalsIgnoreCase(t.getName())))
                    .map(t -> t.getId())
                        .collect(Collectors.toList());

                if (idsToDelete.isEmpty()) {
                    return new ToolResponse(false, "No matching tasks found to delete.");
                }

                dailyRoutineService.deleteDailyTasks(idsToDelete);
                return new ToolResponse(true, "Successfully deleted " + idsToDelete.size() + " task(s) from daily routine for " + request.date() + ".");
            } catch (Exception e) {
                return new ToolResponse(false, "Failed to delete daily routine tasks: " + e.getMessage());
            }
        };
    }
}