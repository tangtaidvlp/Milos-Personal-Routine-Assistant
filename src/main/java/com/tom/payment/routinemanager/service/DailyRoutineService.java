package com.tom.payment.routinemanager.service;

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
import com.tom.payment.routinemanager.repository.DefaultRoutineRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyRoutineService {

    private final UserService userService;
    private final DefaultRoutineRepository defaultRoutineRepository;
    private final DailyRoutineRepository dailyRoutineRepository;
    private final DailyTaskRepository dailyTaskRepository;

    // This one is for new user creation
    // When they create account after the cron job has already run for the day
    // we need to create a daily routine for them
    @Transactional
    public DailyRoutine getOrCreateDailyRoutine(UUID userId, LocalDate date) {
        User user = userService.getUserById(userId);
        
        return dailyRoutineRepository.findByUserAndDate(user, date)
                .orElseGet(() -> createDailyRoutineFromDefault(user, date));
    }

    private DailyRoutine createDailyRoutineFromDefault(User user, LocalDate date) {
        log.info("Creating daily routine for user {} on date {}", user.getId(), date);
        DefaultRoutine defaultRoutine = defaultRoutineRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Daily routine not found for user"));

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
                    task.setDailyRoutine(dailyRoutine);
                    return task;
                })
                .collect(Collectors.toList());

        dailyRoutine.setTasks(dailyTasks);
        return dailyRoutineRepository.save(dailyRoutine);
    }
    @Transactional
    public List<DailyTask> addDailyTasks(UUID dailyRoutineId, List<DailyTask> tasks) {
        DailyRoutine routine = dailyRoutineRepository.findById(dailyRoutineId)
                .orElseThrow(() -> new RuntimeException("Daily routine not found"));

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

    @Transactional
    public void spawnDailyRoutineForUser(User user) {
        LocalDate today = LocalDate.now();
        if (dailyRoutineRepository.findByUserAndDate(user, today).isPresent()) {
            log.info("Skip spawn for user {}: daily routine already exists for {}", user.getId(), today);
            return;
        }

        // Fetch the default routine having type is "WEEKDAY" for the user
        DefaultRoutine defaultRoutine = defaultRoutineRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Default weekday routine not found for user"));

        DailyRoutine dailyRoutine = new DailyRoutine();
        dailyRoutine.setUser(user);
        dailyRoutine.setDate(today);

        List<DailyTask> dailyTasks = defaultRoutine.getTasks().stream()
                .map(template -> {
                    DailyTask task = new DailyTask();
                    task.setName(template.getName());
                    task.setDescription(template.getDescription());
                    task.setStartTime(template.getStartTime());
                    task.setDurationMinutes(template.getDurationMinutes());
                    task.setCompleted(false);
                    task.setDailyRoutine(dailyRoutine);
                    return task;
                })
                .collect(Collectors.toList());

        dailyRoutine.setTasks(dailyTasks);
        dailyRoutineRepository.save(dailyRoutine);
    }

    @Transactional
    public void spawnWeekendRoutineForUser(User user, DefaultRoutine weekendRoutine) {
        LocalDate today = LocalDate.now();
        if (dailyRoutineRepository.findByUserAndDate(user, today).isPresent()) {
            log.info("Skip weekend spawn for user {}: daily routine already exists for {}", user.getId(), today);
            return;
        }

        DailyRoutine dailyRoutine = new DailyRoutine();
        dailyRoutine.setUser(user);
        dailyRoutine.setDate(today);

        List<DailyTask> dailyTasks = weekendRoutine.getTasks().stream()
                .map(template -> {
                    DailyTask task = new DailyTask();
                    task.setName(template.getName());
                    task.setDescription(template.getDescription());
                    task.setStartTime(template.getStartTime());
                    task.setDurationMinutes(template.getDurationMinutes());
                    task.setCompleted(false);
                    task.setDailyRoutine(dailyRoutine);
                    return task;
                })
                .collect(Collectors.toList());

        dailyRoutine.setTasks(dailyTasks);
        dailyRoutineRepository.save(dailyRoutine);
    }
}
