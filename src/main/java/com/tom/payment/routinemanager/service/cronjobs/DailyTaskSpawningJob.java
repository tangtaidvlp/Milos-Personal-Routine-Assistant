package com.tom.payment.routinemanager.service.cronjobs;

import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.tom.payment.routinemanager.model.User;
import com.tom.payment.routinemanager.service.DailyRoutineService;
import com.tom.payment.routinemanager.service.UserService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DailyTaskSpawningJob {

    private final UserService userService;
    private final DailyRoutineService dailyRoutineService;

    @Scheduled(cron = "0 0 0 * * ?") // This cron expression means "at midnight every day"
    public void spawnDailyRoutine() {
        List<User> users = userService.getAllUsers();
        for (User user : users) {
            try {
                spawnDailyRoutineForUser(user);
            } catch (Exception e) {
                // Log the error and continue with the next user
                System.err.println("Error spawning daily routine for user " + user.getId() + ": " + e.getMessage());
            }
        }
    }

    private void spawnDailyRoutineForUser(User user) {
        // Weekday vs weekend template is chosen in DefaultRoutineService.resolveTemplateFor.
        dailyRoutineService.spawnDailyRoutineForUser(user);
    }
}
