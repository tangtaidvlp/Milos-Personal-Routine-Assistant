package com.tom.payment.routinemanager.api;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tom.payment.routinemanager.service.cronjobs.DailyTaskSpawningJob;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/support/cron")
@Profile({"local", "fast", "test"})
@RequiredArgsConstructor
public class CronSupportController {

    private final DailyTaskSpawningJob dailyTaskSpawningJob;
    private final Clock clock;

    @PostMapping("/spawn-daily-task")
    public ResponseEntity<Map<String, Object>> triggerDailyTaskSpawn() {
        dailyTaskSpawningJob.spawnDailyRoutine();

        Map<String, Object> response = new HashMap<>();
        response.put("ok", true);
        response.put("triggeredAt", LocalDateTime.now(clock).toString());
        response.put("message", "Daily task spawn job triggered successfully.");
        return ResponseEntity.ok(response);
    }
}
