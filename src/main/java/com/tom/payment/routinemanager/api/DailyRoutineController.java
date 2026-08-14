package com.tom.payment.routinemanager.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
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

import com.tom.payment.routinemanager.model.DailyRoutine;
import com.tom.payment.routinemanager.model.DailyTask;
import com.tom.payment.routinemanager.service.DailyRoutineService;

@RestController
@RequestMapping("/api/routine-manager")
public class DailyRoutineController {

    private final DailyRoutineService dailyRoutineService;

    public DailyRoutineController(DailyRoutineService dailyRoutineService) {
        this.dailyRoutineService = dailyRoutineService;
    }

    @PostMapping("/daily-routine/{routineId}/tasks")
    public ResponseEntity<List<DailyTask>> addDailyTasks(
            @PathVariable UUID routineId,
            @RequestBody List<DailyTask> tasks) {
        List<DailyTask> createdTasks = dailyRoutineService.addDailyTasks(routineId, tasks);
        return ResponseEntity.ok(createdTasks);
    }

    @PutMapping("/daily-routine/tasks")
    public ResponseEntity<List<DailyTask>> updateDailyTasks(
            @RequestBody List<DailyTask> tasksDetails) {
        List<DailyTask> updatedTasks = dailyRoutineService.updateDailyTasks(tasksDetails);
        return ResponseEntity.ok(updatedTasks);
    }

    @DeleteMapping("/daily-routine/tasks")
    public ResponseEntity<Void> deleteDailyTasks(@RequestParam List<UUID> taskIds) {
        dailyRoutineService.deleteDailyTasks(taskIds);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/daily-routine/{userId}")
    public ResponseEntity<DailyRoutine> getDailyRoutineOrCreateIfNewAccount(
            @PathVariable UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        LocalDate targetDate = (date != null) ? date : LocalDate.now();
        DailyRoutine dailyRoutine = dailyRoutineService.getOrCreateDailyRoutine(userId, targetDate);
        return ResponseEntity.ok(dailyRoutine);
    }

    @PostMapping("/daily-routine/{userId}")
    public ResponseEntity<DailyRoutine> ensureDailyRoutine(
            @PathVariable UUID userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        DailyRoutine dailyRoutine = dailyRoutineService.ensureDailyRoutine(userId, date);
        return ResponseEntity.ok(dailyRoutine);
    }
}
