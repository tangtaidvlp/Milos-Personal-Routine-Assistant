package com.tom.payment.routinemanager.service.chat;

import java.util.List;

import org.springframework.ai.chat.messages.Message;

/**
 * What Buddy is given about earlier conversation: the compacted summary (may be
 * null) and the stored messages after it, oldest first.
 */
public record ChatContext(String summary, List<Message> recentMessages) {

    public ChatContext {
        recentMessages = recentMessages == null ? List.of() : List.copyOf(recentMessages);
    }

    public static ChatContext empty() {
        return new ChatContext(null, List.of());
    }
}
