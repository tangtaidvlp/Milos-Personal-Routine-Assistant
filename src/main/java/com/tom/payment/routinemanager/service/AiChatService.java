package com.tom.payment.routinemanager.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import com.tom.payment.routinemanager.model.DailyRoutine;
import com.tom.payment.routinemanager.model.DefaultRoutine;
import com.tom.payment.routinemanager.service.chat.ChatContext;

/**
 * Handles the chat interaction for each user. Backed by either the real
 * Gemini-powered implementation or a mock implementation, depending on the
 * "spring.ai.active" property. Implementations are stateless: earlier
 * conversation is passed in as {@link ChatContext} (see ChatConversationService).
 * A reply may carry a goal proposal (F013), which the user confirms in the app.
 */
public interface AiChatService {

    /** @param localTime the user's local time (F015), or null when unknown */
    BuddyReply defaultRoutineChat(UUID userId, DefaultRoutine defaultRoutine, ChatContext context, String userMessage, LocalTime localTime);

    BuddyReply dailyRoutineChat(UUID userId, DailyRoutine dailyRoutine, LocalDate date, ChatContext context, String userMessage, LocalTime localTime);
}
