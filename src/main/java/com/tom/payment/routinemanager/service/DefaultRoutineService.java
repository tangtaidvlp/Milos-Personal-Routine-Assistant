package com.tom.payment.routinemanager.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.tom.payment.routinemanager.funcs.TaskTimeFunctions;
import com.tom.payment.routinemanager.funcs.RoutineTemplateRules;
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
    private final TaskColorService taskColorService;

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
                    assignColors(existing, defaultRoutine.getTasks());
                    if (defaultRoutine.getTasks() != null) {
                        defaultRoutine.getTasks().forEach(task -> {
                            task.setDefaultRoutine(existing);
                            existing.getTasks().add(task);
                        });
                    }
                    return taskColorService.fillNextColor(defaultRoutineRepository.save(existing));
                })
                .orElseGet(() -> {
                    defaultRoutine.setUser(user);
                    defaultRoutine.setColorCursor(0);
                    assignColors(defaultRoutine, defaultRoutine.getTasks());
                    if (defaultRoutine.getTasks() != null) {
                        defaultRoutine.getTasks().forEach(task -> task.setDefaultRoutine(defaultRoutine));
                    }
                    return taskColorService.fillNextColor(defaultRoutineRepository.save(defaultRoutine));
                });
    }

    // Fully overwrite the existing default routine with the new one, including tasks
    @Transactional
    public DefaultRoutine updateDefaultRoutine(UUID id, DefaultRoutine defaultRoutine) {
        DefaultRoutine existing = defaultRoutineRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Default routine not found"));
        
        existing.setName(defaultRoutine.getName());
        
        // Simple update: clear and add new tasks (recreated tasks go through colour assignment)
        existing.getTasks().clear();
        assignColors(existing, defaultRoutine.getTasks());
        if (defaultRoutine.getTasks() != null) {
            defaultRoutine.getTasks().forEach(task -> {
                task.setDefaultRoutine(existing);
                existing.getTasks().add(task);
            });
        }
        
        return taskColorService.fillNextColor(defaultRoutineRepository.save(existing));
    }

    @Transactional(readOnly = true)
    public DefaultRoutine getDefaultRoutineByUserId(UUID userId, DefaultRoutine.RoutineTypeEnum type) {
        User user = userService.getUserById(userId);
        return taskColorService.fillNextColor(defaultRoutineRepository.findByUserAndRoutineType(user, type)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Default routine not found for user")));
    }

    /**
     * Weekday routine: must exist (404 otherwise).
     * Weekend routine: created empty on first request for users from before weekend routines existed.
     */
    @Transactional
    public DefaultRoutine getOrCreateDefaultRoutine(UUID userId, DefaultRoutine.RoutineTypeEnum type) {
        // GET-creates by design: see specs/.done.002-onboarding-instruction/research.md R2
        User user = userService.getUserById(userId);
        DefaultRoutine routine = defaultRoutineRepository.findByUserAndRoutineType(user, type)
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
        return taskColorService.fillNextColor(routine);
    }

    /**
     * Copy Weekday to Weekend (specs/F012): every Weekend task is replaced by a copy of a Weekday task
     * (name, description, start, duration, colour), in one transaction. The Weekday routine is unchanged.
     */
    @Transactional
    public void copyWeekdayToWeekend(User user) {
        DefaultRoutine weekday = defaultRoutineRepository.findByUserAndRoutineType(user, DefaultRoutine.RoutineTypeEnum.DAILY)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Default routine not found"));
        DefaultRoutine weekend = getOrCreateDefaultRoutine(user.getId(), DefaultRoutine.RoutineTypeEnum.WEEKEND);

        weekend.getTasks().clear();
        // Delete the old rows before the copies are inserted.
        defaultRoutineRepository.saveAndFlush(weekend);
        weekday.getTasks().forEach(source -> {
            RoutineTaskTemplate task = new RoutineTaskTemplate();
            task.setName(source.getName());
            task.setDescription(source.getDescription());
            task.setStartTime(source.getStartTime());
            task.setDurationMinutes(source.getDurationMinutes());
            task.setColor(source.getColor());
            task.setOverlay(source.getOverlay());
            task.setDefaultRoutine(weekend);
            weekend.getTasks().add(task);
        });
        weekend.setColorCursor(weekday.getColorCursor());
        defaultRoutineRepository.save(weekend);
    }

    /**
     * The default routine a given date is copied from (research R3):
     * Saturday/Sunday use the weekend routine if it has at least one task, otherwise the weekday routine.
     */
    @Transactional(readOnly = true)
    public DefaultRoutine resolveTemplateFor(User user, LocalDate date) {
        // Only weekends need the weekend routine looked up.
        Optional<DefaultRoutine> weekendWithTasks = RoutineTemplateRules.isWeekend(date)
                ? defaultRoutineRepository
                        .findByUserAndRoutineType(user, DefaultRoutine.RoutineTypeEnum.WEEKEND)
                        .filter(routine -> !routine.getTasks().isEmpty())
                : Optional.empty();
        if (RoutineTemplateRules.templateTypeFor(date, weekendWithTasks.isPresent())
                == DefaultRoutine.RoutineTypeEnum.WEEKEND) {
            return weekendWithTasks.get();
        }
        return defaultRoutineRepository.findByUserAndRoutineType(user, DefaultRoutine.RoutineTypeEnum.DAILY)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Default routine not found for user"));
    }

    @Transactional
    public List<RoutineTaskTemplate> addDefaultTasks(UUID defaultRoutineId, List<RoutineTaskTemplate> tasks) {
        DefaultRoutine routine = defaultRoutineRepository.findById(defaultRoutineId)
                .orElseThrow(() -> new RuntimeException("Default routine not found"));
        tasks.forEach(task -> TaskTimeFunctions.requireValidDuration(task.getDurationMinutes()));
        assignColors(routine, tasks);

        List<RoutineTaskTemplate> savedTasks = tasks.stream().map(task -> {
            task.setDefaultRoutine(routine);
            routine.getTasks().add(task);
            return task;
        }).collect(Collectors.toList());
        TaskTimeFunctions.requireOverlapCap(routine.getTasks(), savedTasks,
                RoutineTaskTemplate::getStartTime, RoutineTaskTemplate::getDurationMinutes);

        return routineTaskTemplateRepository.saveAll(savedTasks);
    }

    @Transactional
    public List<RoutineTaskTemplate> updateDefaultTasks(List<RoutineTaskTemplate> tasksDetails) {
        tasksDetails.forEach(details -> TaskTimeFunctions.requireValidDuration(details.getDurationMinutes()));
        Map<DefaultRoutine, List<RoutineTaskTemplate>> changedByRoutine = new LinkedHashMap<>();
        List<RoutineTaskTemplate> updated = tasksDetails.stream().map(details -> {
            RoutineTaskTemplate existing = routineTaskTemplateRepository.findById(details.getId())
                    .orElseThrow(() -> new RuntimeException("Task template not found: " + details.getId()));
            boolean timeChanged = !Objects.equals(existing.getStartTime(), details.getStartTime())
                    || existing.getDurationMinutes() != details.getDurationMinutes();
            existing.setName(details.getName());
            existing.setDescription(details.getDescription());
            existing.setStartTime(details.getStartTime());
            existing.setDurationMinutes(details.getDurationMinutes());
            // A colour is only changed when one is sent; null keeps it (specs/F005 R4).
            if (details.getColor() != null) {
                taskColorService.requireValid(details.getColor());
                existing.setColor(details.getColor());
            }
            if (details.getOverlay() != null) {
                existing.setOverlay(details.getOverlay());
            }
            if (timeChanged && existing.getDefaultRoutine() != null) {
                changedByRoutine.computeIfAbsent(existing.getDefaultRoutine(), r -> new ArrayList<>()).add(existing);
            }
            return existing;
        }).collect(Collectors.toList());

        changedByRoutine.forEach((routine, changed) -> TaskTimeFunctions.requireOverlapCap(routine.getTasks(),
                changed, RoutineTaskTemplate::getStartTime, RoutineTaskTemplate::getDurationMinutes));
        return routineTaskTemplateRepository.saveAll(updated);
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

    /** Round-robin colours for tasks about to be created in {@code routine} (specs/F005 data-model §4). */
    private void assignColors(DefaultRoutine routine, List<RoutineTaskTemplate> newTasks) {
        int cursor = taskColorService.assign(routine.getColorCursor(), newTasks,
                RoutineTaskTemplate::getName, RoutineTaskTemplate::getColor, RoutineTaskTemplate::setColor);
        routine.setColorCursor(cursor);
    }

}
