package com.tom.payment.routinemanager.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tom.payment.routinemanager.model.DefaultRoutine;
import com.tom.payment.routinemanager.model.RoutineTaskTemplate;
import com.tom.payment.routinemanager.service.DefaultRoutineService;

@RestController
@RequestMapping("/api/v1/default-routine")
public class DefaultRoutineController {

    private final DefaultRoutineService routineService;

    public DefaultRoutineController(DefaultRoutineService routineService) {
        this.routineService = routineService;
    }

    @PostMapping("/{routineId}/tasks")
    public ResponseEntity<List<RoutineTaskTemplate>> addDefaultTasks(
            @PathVariable UUID routineId,
            @RequestBody List<RoutineTaskTemplate> tasks) {
        List<RoutineTaskTemplate> createdTasks = routineService.addDefaultTasks(routineId, tasks);
        return ResponseEntity.ok(createdTasks);
    }

    @PutMapping("/tasks")
    public ResponseEntity<List<RoutineTaskTemplate>> updateDefaultTasks(
            @RequestBody List<RoutineTaskTemplate> tasksDetails) {
        List<RoutineTaskTemplate> updatedTasks = routineService.updateDefaultTasks(tasksDetails);
        return ResponseEntity.ok(updatedTasks);
    }

    @DeleteMapping("/tasks")
    public ResponseEntity<Void> deleteDefaultTasks(@RequestParam List<UUID> taskIds) {
        routineService.deleteDefaultTasks(taskIds);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<DefaultRoutine> getDefaultRoutineForUser(@PathVariable UUID userId) {
        DefaultRoutine routine = routineService.getDefaultRoutineByUserId(userId);
        return ResponseEntity.ok(routine);
    }

    @PostMapping("/users/{userId}")
    public ResponseEntity<DefaultRoutine> createDefaultRoutine(
            @PathVariable UUID userId,
            @RequestBody DefaultRoutine defaultRoutine) {
        DefaultRoutine createdRoutine = routineService.createDefaultRoutine(userId, defaultRoutine);
        return ResponseEntity.ok(createdRoutine);
    }

    @PutMapping("/{id}")
    public ResponseEntity<DefaultRoutine> updateDefaultRoutine(
            @PathVariable UUID id,
            @RequestBody DefaultRoutine defaultRoutine) {
        DefaultRoutine updatedRoutine = routineService.updateDefaultRoutine(id, defaultRoutine);
        return ResponseEntity.ok(updatedRoutine);
    }
}
