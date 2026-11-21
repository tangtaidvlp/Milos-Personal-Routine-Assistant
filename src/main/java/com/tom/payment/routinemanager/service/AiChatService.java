package com.tom.payment.routinemanager.service;

import java.time.LocalDate;
import java.util.UUID;

import com.tom.payment.routinemanager.model.DailyRoutine;
import com.tom.payment.routinemanager.model.DefaultRoutine;
import com.tom.payment.routinemanager.service.chat.ChatContext;

/**
 * Handles the chat interaction for each user. Backed by either the real
 * Gemini-powered implementation or a mock implementation, depending on the
 * "spring.ai.active" property. Implementations are stateless: earlier
 * conversation is passed in as {@link ChatContext} (see ChatConversationService).
 */
public interface AiChatService {

    String chat(UUID userId, String userMessage);

    String defaultRoutineChat(UUID userId, DefaultRoutine defaultRoutine, ChatContext context, String userMessage);

    String dailyRoutineChat(UUID userId, DailyRoutine dailyRoutine, LocalDate date, ChatContext context, String userMessage);
}
