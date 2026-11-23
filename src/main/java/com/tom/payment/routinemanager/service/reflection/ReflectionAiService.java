package com.tom.payment.routinemanager.service.reflection;

import java.time.LocalDate;
import java.util.UUID;

import com.tom.payment.routinemanager.dto.ReflectionView;
import com.tom.payment.routinemanager.service.chat.ChatContext;

/**
 * Buddy for reflections (specs/.done.F007-reflection research R4/R5). Kept apart from
 * {@code AiChatService} so the reflection chat has no plan/default-routine tools.
 */
public interface ReflectionAiService {

    /** A short, encouraging note about the day's plan vs reality. */
    String feedback(ReflectionView view);

    /** One chat turn; may edit the reflection (never the plan) through its tools. */
    String chat(UUID userId, LocalDate date, ReflectionView view, ChatContext context, String userMessage);
}
