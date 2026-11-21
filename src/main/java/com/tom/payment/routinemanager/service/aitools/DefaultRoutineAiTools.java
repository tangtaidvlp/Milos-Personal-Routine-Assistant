package com.tom.payment.routinemanager.service.aitools;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import com.tom.payment.routinemanager.model.DefaultRoutine;
import com.tom.payment.routinemanager.model.RoutineTaskTemplate;
import com.tom.payment.routinemanager.model.User;
import com.tom.payment.routinemanager.repository.DefaultRoutineRepository;
import com.tom.payment.routinemanager.service.DefaultRoutineService;
import com.tom.payment.routinemanager.service.TaskColorService;
import com.tom.payment.routinemanager.service.UserService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DefaultRoutineAiTools {

    private final DefaultRoutineService routineService;
    private final DefaultRoutineRepository defaultRoutineRepository;
    private final UserService userService;
    private final TaskColorService taskColorService;

    public record TaskInput(String name, String description, String startTime, int durationMinutes,
            @ToolParam(required = false, description = "Optional task color: a palette id (" + TaskColorService.PALETTE_IDS_HINT + "). Only set it when the user asks for a color; never use hex codes.") String color) {}
    public record TaskUpdateInput(String taskId, String newName, String description, String startTime, Integer durationMinutes,
            @ToolParam(required = false, description = "Optional task color: a palette id (" + TaskColorService.PALETTE_IDS_HINT + "). Only set it when the user asks for a color; never use hex codes.") String color) {}

    @Tool(description = "Add one or more tasks to a specific default routine template by defaultRoutineId. " +
            "Each task needs a name, description, startTime (HH:mm format), and durationMinutes. " +
            "Leave color empty unless the user asks for one; the app assigns colors. " +
            "If a task crosses midnight, split it into 2 tasks with the SAME name so they share a color.")
    public String addDefaultRoutineTasks(
            @ToolParam(description = "The UUID of the user") String userId,
            @ToolParam(description = "The UUID of the default routine") String defaultRoutineId,
            @ToolParam(description = "List of tasks to add") List<TaskInput> tasks) {
        try {
            User user = userService.getUserById(UUID.fromString(userId));
            DefaultRoutine routine = defaultRoutineRepository.findById(UUID.fromString(defaultRoutineId))
                    .orElseThrow(() -> new RuntimeException("Default routine not found: " + defaultRoutineId));
            ensureOwnership(user, routine);

            for (TaskInput input : tasks) {
                if (input.color() != null && taskColorService.paletteIdFor(input.color()).isEmpty()) {
                    return "No tasks added. " + taskColorService.unknownColorMessage(input.color());
                }
            }

            List<RoutineTaskTemplate> entities = tasks.stream().map(input -> {
                RoutineTaskTemplate t = new RoutineTaskTemplate();
                t.setColor(taskColorService.paletteIdFor(input.color()).orElse(null));
                t.setName(input.name());
                t.setDescription(input.description());
                t.setStartTime(LocalTime.parse(input.startTime()));
                t.setDurationMinutes(input.durationMinutes());
                return t;
            }).collect(Collectors.toList());

            routineService.addDefaultTasks(routine.getId(), entities);
            return "Successfully added " + entities.size() + " task(s) to default routine " + defaultRoutineId + ".";
        } catch (Exception e) {
            return "Failed to add tasks: " + e.getMessage();
        }
    }

    @Tool(description = "Update one or more tasks in a specific default routine template by taskId. " +
            "Use taskId from the default routine, not task name. Can change name, time, duration or color (palette id).")
    public String updateDefaultRoutineTasks(
            @ToolParam(description = "The UUID of the user") String userId,
            @ToolParam(description = "The UUID of the default routine") String defaultRoutineId,
            @ToolParam(description = "List of task updates") List<TaskUpdateInput> updates) {
        try {
            User user = userService.getUserById(UUID.fromString(userId));
            DefaultRoutine routine = defaultRoutineRepository.findById(UUID.fromString(defaultRoutineId))
                    .orElseThrow(() -> new RuntimeException("Default routine not found: " + defaultRoutineId));
            ensureOwnership(user, routine);

            List<RoutineTaskTemplate> toUpdate = new ArrayList<>();
            for (TaskUpdateInput u : updates) {
                UUID taskId = UUID.fromString(u.taskId());
                RoutineTaskTemplate existing = routine.getTasks().stream()
                        .filter(t -> t.getId().equals(taskId))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("Task id '" + u.taskId() + "' not found in default routine " + defaultRoutineId));

                RoutineTaskTemplate details = new RoutineTaskTemplate();
                details.setId(existing.getId());
                details.setName(u.newName() != null ? u.newName() : existing.getName());
                details.setDescription(u.description() != null ? u.description() : existing.getDescription());
                details.setStartTime(u.startTime() != null ? LocalTime.parse(u.startTime()) : existing.getStartTime());
                Integer requestedDuration = u.durationMinutes();
                details.setDurationMinutes(requestedDuration != null ? requestedDuration : existing.getDurationMinutes());
                if (u.color() != null) {
                    var colorId = taskColorService.paletteIdFor(u.color());
                    if (colorId.isEmpty()) {
                        return "No tasks updated. " + taskColorService.unknownColorMessage(u.color());
                    }
                    details.setColor(colorId.get());
                }
                toUpdate.add(details);
            }

            routineService.updateDefaultTasks(toUpdate);
            return "Successfully updated " + toUpdate.size() + " task(s) in default routine " + defaultRoutineId + ".";
        } catch (Exception e) {
            return "Failed to update tasks: " + e.getMessage();
        }
    }

    @Tool(description = "Delete one or more tasks from a specific default routine template by taskIds. " +
            "Use taskId from the default routine, not task name.")
    public String deleteDefaultRoutineTasks(
            @ToolParam(description = "The UUID of the user") String userId,
            @ToolParam(description = "The UUID of the default routine") String defaultRoutineId,
            @ToolParam(description = "List of task IDs to delete") List<String> taskIds) {
        try {
            User user = userService.getUserById(UUID.fromString(userId));
            DefaultRoutine routine = defaultRoutineRepository.findById(UUID.fromString(defaultRoutineId))
                    .orElseThrow(() -> new RuntimeException("Default routine not found: " + defaultRoutineId));
            ensureOwnership(user, routine);

            List<UUID> requestedIds = taskIds.stream()
                    .map(UUID::fromString)
                    .collect(Collectors.toList());

            List<UUID> ids = routine.getTasks().stream()
                    .filter(t -> requestedIds.contains(t.getId()))
                    .map(t -> t.getId())
                    .collect(Collectors.toList());

            if (ids.isEmpty()) return "No matching tasks found to delete.";

            routineService.deleteDefaultTasks(ids);
            return "Successfully deleted " + ids.size() + " task(s) from default routine " + defaultRoutineId + ".";
        } catch (Exception e) {
            return "Failed to delete tasks: " + e.getMessage();
        }
    }

    private void ensureOwnership(User user, DefaultRoutine routine) {
        if (routine.getUser() == null || !routine.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Default routine does not belong to the current user");
        }
    }
}
