package com.tom.payment.routinemanager.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tom.payment.routinemanager.funcs.TaskTimeFunctions;
import com.tom.payment.routinemanager.model.DailyRoutine;
import com.tom.payment.routinemanager.model.DailyTask;
import com.tom.payment.routinemanager.model.DefaultRoutine;
import com.tom.payment.routinemanager.model.User;
import com.tom.payment.routinemanager.repository.DailyRoutineRepository;
import com.tom.payment.routinemanager.repository.DailyTaskRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyRoutineService {

    private final UserService userService;
    private final DefaultRoutineService defaultRoutineService;
    private final DailyRoutineRepository dailyRoutineRepository;
    private final DailyTaskRepository dailyTaskRepository;
    private final TaskColorService taskColorService;
    private final Clock clock;

    // This one is for new user creation
    // When they create account after the cron job has already run for the day
    // we need to create a daily routine for them
    //
    // Only "today" auto-clones from the default routine here. Browsing to any
    // other date must not silently write data just from looking at it, so a
    // missing non-today date comes back as a transient (unsaved) empty
    // routine instead. Use ensureDailyRoutine to explicitly create one.
    @Transactional
    public DailyRoutine getOrCreateDailyRoutine(UUID userId, LocalDate date) {
        User user = userService.getUserById(userId);

        DailyRoutine routine = dailyRoutineRepository.findByUserAndDate(user, date)
                .orElseGet(() -> {
                    if (date.isEqual(LocalDate.now(clock))) {
                        return createDailyRoutineFromDefault(user, date);
                    }
                    DailyRoutine transientRoutine = new DailyRoutine();
                    transientRoutine.setUser(user);
                    transientRoutine.setDate(date);
                    return transientRoutine;
                });
        return taskColorService.fillNextColor(routine);
    }

    // Explicit, intent-driven get-or-create for any date (e.g. the user adds
    // their first task to a date that hasn't been touched yet) — always
    // persists and clones from the default routine if missing, regardless of
    // date.
    @Transactional
    public DailyRoutine ensureDailyRoutine(UUID userId, LocalDate date) {
        User user = userService.getUserById(userId);

        return taskColorService.fillNextColor(dailyRoutineRepository.findByUserAndDate(user, date)
                .orElseGet(() -> createDailyRoutineFromDefault(user, date)));
    }

    private DailyRoutine createDailyRoutineFromDefault(User user, LocalDate date) {
        log.info("Creating daily routine for user {} on date {}", user.getId(), date);
        // Weekend dates copy the weekend routine when it has tasks, otherwise the weekday one.
        DefaultRoutine defaultRoutine = defaultRoutineService.resolveTemplateFor(user, date);

        DailyRoutine dailyRoutine = new DailyRoutine();
        dailyRoutine.setUser(user);
        dailyRoutine.setDate(date);

        List<DailyTask> dailyTasks = defaultRoutine.getTasks().stream()
                .map(template -> {
                    DailyTask task = new DailyTask();
                    task.setName(template.getName());
                    task.setDescription(template.getDescription());
                    task.setStartTime(template.getStartTime());
                    task.setDurationMinutes(template.getDurationMinutes());
                    task.setCompleted(false);
                    task.setColor(template.getColor());
                    task.setDailyRoutine(dailyRoutine);
                    return task;
                })
                .collect(Collectors.toList());

        dailyRoutine.setTasks(dailyTasks);
        // Day-only tasks continue the template's colour sequence (spec FR-003).
        dailyRoutine.setColorCursor(defaultRoutine.getColorCursor());
        return dailyRoutineRepository.save(dailyRoutine);
    }
    @Transactional
    public List<DailyTask> addDailyTasks(UUID dailyRoutineId, List<DailyTask> tasks) {
        DailyRoutine routine = dailyRoutineRepository.findById(dailyRoutineId)
                .orElseThrow(() -> new RuntimeException("Daily routine not found"));
        routine.setColorCursor(taskColorService.assign(routine.getColorCursor(), tasks,
                DailyTask::getName, DailyTask::getColor, DailyTask::setColor));

        List<DailyTask> savedTasks = tasks.stream().map(task -> {
            task.setDailyRoutine(routine);
            TaskTimeFunctions.shiftOverlappingTasks(routine.getTasks(), task);
            routine.getTasks().add(task);
            return task;
        }).collect(Collectors.toList());

        return dailyTaskRepository.saveAll(savedTasks);
    }

    @Transactional
    public List<DailyTask> updateDailyTasks(List<DailyTask> tasksDetails) {
        return tasksDetails.stream().map(details -> {
            DailyTask existing = dailyTaskRepository.findById(details.getId())
                    .orElseThrow(() -> new RuntimeException("Daily task not found: " + details.getId()));
            existing.setName(details.getName());
            existing.setDescription(details.getDescription());
            existing.setStartTime(details.getStartTime());
            existing.setDurationMinutes(details.getDurationMinutes());
            existing.setCompleted(details.isCompleted());
            // A colour is only changed when one is sent; null keeps it (specs/F005 R4).
            if (details.getColor() != null) {
                taskColorService.requireValid(details.getColor());
                existing.setColor(details.getColor());
            }

            DailyRoutine routine = existing.getDailyRoutine();
            if (routine != null) {
                List<DailyTask> siblings = routine.getTasks().stream()
                        .filter(task -> !task.getId().equals(existing.getId()))
                        .collect(Collectors.toList());
                TaskTimeFunctions.shiftOverlappingTasks(siblings, existing);
            }

            return dailyTaskRepository.save(existing);
        }).collect(Collectors.toList());
    }

    @Transactional
    public void deleteDailyTasks(List<UUID> taskIds) {
        List<DailyTask> tasks = dailyTaskRepository.findAllById(taskIds);
        tasks.forEach(task -> {
            if (task.getDailyRoutine() != null) {
                task.getDailyRoutine().getTasks().remove(task);
            }
        });
        dailyTaskRepository.deleteAll(tasks);
    }

    // Called by the midnight job. Weekday vs weekend is decided by
    // DefaultRoutineService.resolveTemplateFor (specs/.done.002-onboarding-instruction research R3).
    @Transactional
    public void spawnDailyRoutineForUser(User user) {
        LocalDate today = LocalDate.now(clock);
        if (dailyRoutineRepository.findByUserAndDate(user, today).isPresent()) {
            log.info("Skip spawn for user {}: daily routine already exists for {}", user.getId(), today);
            return;
        }
        createDailyRoutineFromDefault(user, today);
    }
}
