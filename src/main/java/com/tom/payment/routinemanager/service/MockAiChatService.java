package com.tom.payment.routinemanager.service;

import java.time.LocalDate;
import java.util.UUID;
import com.tom.payment.routinemanager.model.DailyRoutine;
import com.tom.payment.routinemanager.model.DefaultRoutine;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Mock implementation used when Gemini/AI is disabled, so no Spring AI
 * beans need to be present or injected.
 */
@Service
@ConditionalOnProperty(name = "spring.ai.active", havingValue = "false", matchIfMissing = true)
public class MockAiChatService implements AiChatService {

    @Override
    public String chat(UUID userId, String userMessage) {
        // Mock response for testing
        return "Mock response for user " + userId + ": " + userMessage;
    }

    @Override
    public String defaultRoutineChat(UUID userId, DefaultRoutine defaultRoutine, String userMessage) {
        // Mock response for testing
        return "Mock response for user " + userId + " (routine " + defaultRoutine.getId() + "): " + userMessage;
    }

    @Override
    public String dailyRoutineChat(UUID userId, DailyRoutine dailyRoutine, LocalDate date, String userMessage) {
        // Mock response for testing
        return "Mock response for user " + userId + " (daily routine " + date + "): " + userMessage;
    }
}
