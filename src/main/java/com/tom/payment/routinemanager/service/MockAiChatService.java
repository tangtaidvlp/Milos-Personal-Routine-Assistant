package com.tom.payment.routinemanager.service;

import java.util.UUID;

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
    public String chatEnhance(UUID userId, UUID defaultRoutineId, String userMessage) {
        // Mock response for testing
        return "Mock response for user " + userId + " (routine " + defaultRoutineId + "): " + userMessage;
    }
}
