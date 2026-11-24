package com.tom.payment.routinemanager.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tom.payment.routinemanager.funcs.TimeOfDay;
import com.tom.payment.routinemanager.config.CurrentUserGuard;
import com.tom.payment.routinemanager.dto.ReflectionTaskInput;
import com.tom.payment.routinemanager.dto.ReflectionView;
import com.tom.payment.routinemanager.service.reflection.ReflectionFeedbackService;
import com.tom.payment.routinemanager.service.reflection.ReflectionService;

import lombok.RequiredArgsConstructor;

/**
 * Daily reflection: what really happened vs the plan
 * (specs/.done.F007-reflection/contracts/reflection-api.md). Every response is a ReflectionView.
 */
@RestController
@RequestMapping("/api/routine-manager/daily-reflection/{userId}/{date}")
@RequiredArgsConstructor
public class DailyReflectionController {

    private final ReflectionService reflectionService;
    private final ReflectionFeedbackService feedbackService;
    private final CurrentUserGuard userGuard;

    /** Read only: never creates a reflection or a plan. */
    @GetMapping
    public ReflectionView get(@PathVariable UUID userId,
                              @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return reflectionService.getView(userGuard.requireUser(userId), date);
    }

    /** Create-or-get; copies the plan once. 400 for a future date. */
    @PostMapping
    public ReflectionView ensure(@PathVariable UUID userId,
                                 @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return reflectionService.ensure(userGuard.requireUser(userId), date);
    }

    @PostMapping("/tasks")
    public ReflectionView addTasks(@PathVariable UUID userId,
                                   @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                   @RequestBody List<ReflectionTaskInput> tasks) {
        return reflectionService.addTasks(userGuard.requireUser(userId), date, tasks);
    }

    @PutMapping("/tasks")
    public ReflectionView updateTasks(@PathVariable UUID userId,
                                      @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                      @RequestBody List<ReflectionTaskInput> tasks) {
        return reflectionService.updateTasks(userGuard.requireUser(userId), date, tasks);
    }

    @DeleteMapping("/tasks")
    public ReflectionView deleteTasks(@PathVariable UUID userId,
                                      @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                      @RequestParam List<UUID> taskIds) {
        return reflectionService.deleteTasks(userGuard.requireUser(userId), date, taskIds);
    }

    /** Buddy's encouraging note; 503 if Buddy is unavailable (earlier note kept). */
    @PostMapping("/feedback")
    public ReflectionView feedback(@PathVariable UUID userId,
                                   @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                   @RequestParam(required = false) String localTime) {
        // F015: the user's local time shapes Buddy's tone; a missing or malformed value is ignored.
        return feedbackService.generate(userGuard.requireUser(userId), date, TimeOfDay.parse(localTime).orElse(null));
    }
}
