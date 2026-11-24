package com.tom.payment.routinemanager.service;

import java.time.LocalTime;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.tom.payment.routinemanager.service.chat.ChatContext;

/** Used when AI is off: an echo reply, no proposal. */
@Service
@ConditionalOnProperty(name = "spring.ai.active", havingValue = "false", matchIfMissing = true)
public class MockPersonalTargetAiService implements PersonalTargetAiService {

    @Override
    public BuddyReply chat(UUID userId, ChatContext context, String userMessage, LocalTime localTime) {
        return BuddyReply.text("Mock goals response for user " + userId + ": " + userMessage);
    }
}
