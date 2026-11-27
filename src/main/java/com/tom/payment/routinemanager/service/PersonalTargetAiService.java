package com.tom.payment.routinemanager.service;

import java.time.LocalTime;
import java.util.UUID;

import com.tom.payment.routinemanager.service.chat.ChatContext;

/**
 * Buddy on the Personal Target page (specs/.done.F013-personal-goals US4, research R11). Kept apart
 * from {@link AiChatService} so this chat has no plan or routine tools: it can only propose goals.
 */
public interface PersonalTargetAiService {

    /** One chat turn; may propose a goal change (never writes). */
    BuddyReply chat(UUID userId, ChatContext context, String userMessage, LocalTime localTime);
}
