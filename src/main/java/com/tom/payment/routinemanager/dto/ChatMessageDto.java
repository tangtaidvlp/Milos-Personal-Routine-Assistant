package com.tom.payment.routinemanager.dto;

import java.time.Instant;

import com.tom.payment.routinemanager.model.ChatMessage;
import com.tom.payment.routinemanager.model.ChatRole;

/** One message in a chat-history page. {@code role} is "user" or "assistant". */
public record ChatMessageDto(long seq, String role, String content, Instant createdAt) {

    public static ChatMessageDto from(ChatMessage m) {
        return new ChatMessageDto(
                m.getSeq(),
                m.getRole() == ChatRole.USER ? "user" : "assistant",
                m.getContent(),
                m.getCreatedAt());
    }
}
