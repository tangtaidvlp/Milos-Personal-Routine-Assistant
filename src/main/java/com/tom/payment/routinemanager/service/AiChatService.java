package com.tom.payment.routinemanager.service;

import java.time.LocalDate;
import java.util.UUID;

import com.tom.payment.routinemanager.model.DailyRoutine;
import com.tom.payment.routinemanager.model.DefaultRoutine;

/**
 * Handles the chat interaction for each user. Backed by either the real
 * Gemini-powered implementation or a mock implementation, depending on the
 * "spring.ai.active" property.
 */
public interface AiChatService {

    String chat(UUID userId, String userMessage);

    // For later usage
    String defaultRoutineChat(UUID userId, DefaultRoutine defaultRoutine, String userMessage);

    String dailyRoutineChat(UUID userId, DailyRoutine dailyRoutine, LocalDate date, String userMessage);
}