package com.tom.payment.routinemanager.service.aitools;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.tom.payment.routinemanager.dto.ReflectionTaskInput;
import com.tom.payment.routinemanager.dto.ReflectionView;
import com.tom.payment.routinemanager.model.ReflectionTask;
import com.tom.payment.routinemanager.model.User;
import com.tom.payment.routinemanager.service.TaskColorService;
import com.tom.payment.routinemanager.service.UserService;
import com.tom.payment.routinemanager.service.reflection.ReflectionService;

import lombok.RequiredArgsConstructor;

/**
 * Buddy's tools for a day's reflection (specs/F007-reflection, US3). They edit
 * only the reflection — the reflection chat client has no plan tools at all.
 */
@Service
@RequiredArgsConstructor
public class ReflectionAiTools {

    private final ReflectionService reflectionService;
    private final UserService userService;
    private final TaskColorService taskColorService;

    public record ReflectionTaskAdd(String name, String description, String startTime, int durationMinutes,
            @ToolParam(required = false, description = "Optional task color: a palette id (" + TaskColorService.PALETTE_IDS_HINT + "). Only set it when the user asks for a color; never use hex codes.") String color) {}

    public record ReflectionTaskUpdate(String taskId,
            @ToolParam(required = false) String newName,
            @ToolParam(required = false) String description,
            @ToolParam(required = false, description = "HH:mm") String startTime,
            @ToolParam(required = false) Integer durationMinutes,
            @ToolParam(required = false, description = "Optional task color: a palette id (" + TaskColorService.PALETTE_IDS_HINT + "). Never use hex codes.") String color) {}

    @Tool(description = "Add things the user actually did (but had not planned) to their reflection for a date. "
            + "Each task needs name, startTime (HH:mm) and durationMinutes (1-1440); description is optional. "
            + "Leave color empty unless the user asks for one.")
    public String addReflectionTasks(
            @ToolParam(description = "The UUID of the user") String userId,
            @ToolParam(description = "The date in yyyy-MM-dd format") String date,
            @ToolParam(description = "Tasks to add") List<ReflectionTaskAdd> tasks) {
        try {
            List<ReflectionTaskInput> inputs = new ArrayList<>();
            for (ReflectionTaskAdd t : tasks) {
                String color = null;
                if (t.color() != null && !t.color().isBlank()) {
                    var id = taskColorService.paletteIdFor(t.color());
                    if (id.isEmpty()) {
                        return "No tasks added. " + taskColorService.unknownColorMessage(t.color());
                    }
                    color = id.get();
                }
                inputs.add(new ReflectionTaskInput(null, t.name(), t.description(),
                        LocalTime.parse(t.startTime()), t.durationMinutes(), color));
            }
            reflectionService.addTasks(user(userId), LocalDate.parse(date), inputs);
            return "Added " + inputs.size() + " task(s) to the reflection for " + date + ".";
        } catch (ResponseStatusException e) {
            return "No tasks added: " + e.getReason();
        } catch (Exception e) {
            return "Failed to add reflection tasks: " + e.getMessage();
        }
    }

    @Tool(description = "Change tasks in the user's reflection for a date, to match what really happened "
            + "(e.g. started later, took longer, was renamed). Identify each task by its id from the reflection state. "
            + "Only send the fields that change.")
    public String updateReflectionTasks(
            @ToolParam(description = "The UUID of the user") String userId,
            @ToolParam(description = "The date in yyyy-MM-dd format") String date,
            @ToolParam(description = "Task updates") List<ReflectionTaskUpdate> updates) {
        try {
            User user = user(userId);
            LocalDate day = LocalDate.parse(date);
            ReflectionView view = reflectionService.getView(user, day);
            Map<UUID, ReflectionTask> byId = view.tasks().stream()
                    .collect(Collectors.toMap(ReflectionTask::getId, Function.identity()));
            List<ReflectionTaskInput> inputs = new ArrayList<>();
            for (ReflectionTaskUpdate u : updates) {
                ReflectionTask existing = byId.get(UUID.fromString(u.taskId()));
                if (existing == null) {
                    return "No tasks updated: task id '" + u.taskId() + "' is not in the reflection for " + date + ".";
                }
                String color = null;
                if (u.color() != null && !u.color().isBlank()) {
                    var id = taskColorService.paletteIdFor(u.color());
                    if (id.isEmpty()) {
                        return "No tasks updated. " + taskColorService.unknownColorMessage(u.color());
                    }
                    color = id.get();
                }
                inputs.add(new ReflectionTaskInput(existing.getId(),
                        u.newName() != null ? u.newName() : existing.getName(),
                        u.description() != null ? u.description() : existing.getDescription(),
                        u.startTime() != null ? LocalTime.parse(u.startTime()) : existing.getStartTime(),
                        u.durationMinutes() != null ? u.durationMinutes() : existing.getDurationMinutes(),
                        color));
            }
            reflectionService.updateTasks(user, day, inputs);
            return "Updated " + inputs.size() + " task(s) in the reflection for " + date + ".";
        } catch (ResponseStatusException e) {
            return "No tasks updated: " + e.getReason();
        } catch (Exception e) {
            return "Failed to update reflection tasks: " + e.getMessage();
        }
    }

    @Tool(description = "Remove tasks from the user's reflection for a date, e.g. things they skipped. Use task ids from the reflection state.")
    public String deleteReflectionTasks(
            @ToolParam(description = "The UUID of the user") String userId,
            @ToolParam(description = "The date in yyyy-MM-dd format") String date,
            @ToolParam(description = "Task ids to remove") List<String> taskIds) {
        try {
            List<UUID> ids = taskIds.stream().map(UUID::fromString).toList();
            reflectionService.deleteTasks(user(userId), LocalDate.parse(date), ids);
            return "Removed " + ids.size() + " task(s) from the reflection for " + date + ".";
        } catch (ResponseStatusException e) {
            return "No tasks removed: " + e.getReason();
        } catch (Exception e) {
            return "Failed to remove reflection tasks: " + e.getMessage();
        }
    }

    private User user(String userId) {
        return userService.getUserById(UUID.fromString(userId));
    }
}
