package com.tom.payment.routinemanager.service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final UserService userService;
    private final DefaultRoutineRepository defaultRoutineRepository;
    private final RoutineTaskTemplateRepository routineTaskTemplateRepository;

    @Transactional
    public DefaultRoutine createDefaultRoutine(UUID userId, DefaultRoutine defaultRoutine) {
        User user = userService.getUserById(userId);
        
        // If a default routine already exists for this user, we might want to update it instead or throw error.
        // For simplicity, let's just use findByUser.
        return defaultRoutineRepository.findByUser(user)
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
    public DefaultRoutine getDefaultRoutineByUserId(UUID userId) {
        User user = userService.getUserById(userId);
        return defaultRoutineRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Default routine not found for user"));
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
