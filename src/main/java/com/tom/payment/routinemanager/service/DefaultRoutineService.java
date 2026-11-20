package com.tom.payment.routinemanager.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.tom.payment.routinemanager.funcs.TaskTimeFunctions;
import com.tom.payment.routinemanager.model.DefaultRoutine;
import com.tom.payment.routinemanager.model.RoutineTaskTemplate;
import com.tom.payment.routinemanager.model.User;
import com.tom.payment.routinemanager.repository.DefaultRoutineRepository;
import com.tom.payment.routinemanager.repository.RoutineTaskTemplateRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DefaultRoutineService {

    public static final String WEEKDAY_ROUTINE_NAME = "Weekday Routine";
    public static final String WEEKEND_ROUTINE_NAME = "Weekend Routine";

    private final UserService userService;
    private final DefaultRoutineRepository defaultRoutineRepository;
    private final RoutineTaskTemplateRepository routineTaskTemplateRepository;

    @Transactional
    public DefaultRoutine createDefaultRoutine(UUID userId, DefaultRoutine defaultRoutine) {
        User user = userService.getUserById(userId);
        DefaultRoutine.RoutineTypeEnum type = defaultRoutine.getRoutineType() != null
                ? defaultRoutine.getRoutineType()
                : DefaultRoutine.RoutineTypeEnum.DAILY;
        defaultRoutine.setRoutineType(type);

        // A user has at most one default routine per type: overwrite it if it already exists.
        return defaultRoutineRepository.findByUserAndRoutineType(user, type)
                .map(existing -> {
                    existing.setName(defaultRoutine.getName());
                    existing.getTasks().clear();
                    if (defaultRoutine.getTasks() != null) {
                        defaultRoutine.getTasks().forEach(task -> {
                            task.setDefaultRoutine(existing);
                            existing.getTasks().add(task);
                        });
                    }
                    return defaultRoutineRepository.save(existing);
                })
                .orElseGet(() -> {
                    defaultRoutine.setUser(user);
                    if (defaultRoutine.getTasks() != null) {
                        defaultRoutine.getTasks().forEach(task -> task.setDefaultRoutine(defaultRoutine));
                    }
                    return defaultRoutineRepository.save(defaultRoutine);
                });
    }

    // Fully overwrite the existing default routine with the new one, including tasks
    @Transactional
    public DefaultRoutine updateDefaultRoutine(UUID id, DefaultRoutine defaultRoutine) {
        DefaultRoutine existing = defaultRoutineRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Default routine not found"));
        
        existing.setName(defaultRoutine.getName());
        
        // Simple update: clear and add new tasks
        existing.getTasks().clear();
        if (defaultRoutine.getTasks() != null) {
            defaultRoutine.getTasks().forEach(task -> {
                task.setDefaultRoutine(existing);
                existing.getTasks().add(task);
            });
        }
        
        return defaultRoutineRepository.save(existing);
    }

    @Transactional(readOnly = true)
    public DefaultRoutine getDefaultRoutineByUserId(UUID userId, DefaultRoutine.RoutineTypeEnum type) {
        User user = userService.getUserById(userId);
        return defaultRoutineRepository.findByUserAndRoutineType(user, type)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Default routine not found for user"));
    }

    /**
     * Weekday routine: must exist (404 otherwise).
     * Weekend routine: created empty on first request for users from before weekend routines existed.
     */
    @Transactional
    public DefaultRoutine getOrCreateDefaultRoutine(UUID userId, DefaultRoutine.RoutineTypeEnum type) {
        // GET-creates by design: see specs/002-onboarding-instruction/research.md R2
        User user = userService.getUserById(userId);
        return defaultRoutineRepository.findByUserAndRoutineType(user, type)
                .orElseGet(() -> {
                    if (type != DefaultRoutine.RoutineTypeEnum.WEEKEND) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Default routine not found for user");
                    }
                    DefaultRoutine weekend = new DefaultRoutine();
                    weekend.setName(WEEKEND_ROUTINE_NAME);
                    weekend.setRoutineType(DefaultRoutine.RoutineTypeEnum.WEEKEND);
                    weekend.setUser(user);
                    return defaultRoutineRepository.save(weekend);
                });
    }

    /**
     * The default routine a given date is copied from (research R3):
     * Saturday/Sunday use the weekend routine if it has at least one task, otherwise the weekday routine.
     */
    @Transactional(readOnly = true)
    public DefaultRoutine resolveTemplateFor(User user, LocalDate date) {
        boolean weekend = date.getDayOfWeek().getValue() >= DayOfWeek.SATURDAY.getValue();
        if (weekend) {
            var weekendRoutine = defaultRoutineRepository
                    .findByUserAndRoutineType(user, DefaultRoutine.RoutineTypeEnum.WEEKEND)
                    .filter(routine -> !routine.getTasks().isEmpty());
            if (weekendRoutine.isPresent()) {
                return weekendRoutine.get();
            }
        }
        return defaultRoutineRepository.findByUserAndRoutineType(user, DefaultRoutine.RoutineTypeEnum.DAILY)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Default routine not found for user"));
    }

    @Transactional
    public List<RoutineTaskTemplate> addDefaultTasks(UUID defaultRoutineId, List<RoutineTaskTemplate> tasks) {
        DefaultRoutine routine = defaultRoutineRepository.findById(defaultRoutineId)
                .orElseThrow(() -> new RuntimeException("Default routine not found"));

        List<RoutineTaskTemplate> savedTasks = tasks.stream().map(task -> {
            task.setDefaultRoutine(routine);
            TaskTimeFunctions.standardiseTasks(routine.getTasks(), task);
            routine.getTasks().add(task);
            return task;
        }).collect(Collectors.toList());

        return routineTaskTemplateRepository.saveAll(savedTasks);
    }

    @Transactional
    public List<RoutineTaskTemplate> updateDefaultTasks(List<RoutineTaskTemplate> tasksDetails) {
        return tasksDetails.stream().map(details -> {
            RoutineTaskTemplate existing = routineTaskTemplateRepository.findById(details.getId())
                    .orElseThrow(() -> new RuntimeException("Task template not found: " + details.getId()));
            existing.setName(details.getName());
            existing.setDescription(details.getDescription());
            existing.setStartTime(details.getStartTime());
            existing.setDurationMinutes(details.getDurationMinutes());

            DefaultRoutine routine = existing.getDefaultRoutine();
            if (routine != null) {
                List<RoutineTaskTemplate> siblings = routine.getTasks().stream()
                        .filter(task -> !task.getId().equals(existing.getId()))
                        .collect(Collectors.toList());
                TaskTimeFunctions.standardiseTasks(siblings, existing);
            }

            return routineTaskTemplateRepository.save(existing);
        }).collect(Collectors.toList());
    }

    @Transactional
    public void deleteDefaultTasks(List<UUID> taskIds) {
        List<RoutineTaskTemplate> tasks = routineTaskTemplateRepository.findAllById(taskIds);
        tasks.forEach(task -> {
            if (task.getDefaultRoutine() != null) {
                task.getDefaultRoutine().getTasks().remove(task);
            }
        });
        routineTaskTemplateRepository.deleteAll(tasks);
    }

}
