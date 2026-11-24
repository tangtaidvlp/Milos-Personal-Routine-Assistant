package com.tom.payment.routinemanager.service.reflection;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import com.tom.payment.routinemanager.dto.ReflectionView;
import com.tom.payment.routinemanager.service.BuddyReply;
import com.tom.payment.routinemanager.service.chat.ChatContext;

/**
 * Buddy for reflections (specs/.done.F007-reflection research R4/R5). Kept apart from
 * {@code AiChatService} so the reflection chat has no plan/default-routine tools.
 */
public interface ReflectionAiService {

    /** A short, encouraging note about the day's plan vs reality; may relate it to the user's goals (F013). */
    String feedback(UUID userId, ReflectionView view, LocalTime localTime);

    /** One chat turn; may edit the reflection (never the plan) and propose a goal change (F013). */
    BuddyReply chat(UUID userId, LocalDate date, ReflectionView view, ChatContext context, String userMessage, LocalTime localTime);
}
