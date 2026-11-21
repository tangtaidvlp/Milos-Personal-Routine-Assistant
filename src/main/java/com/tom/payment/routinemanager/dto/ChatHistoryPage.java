package com.tom.payment.routinemanager.dto;

import java.util.List;

/**
 * A page of chat history, oldest to newest. {@code nextBefore} is the cursor
 * for the next older page, or null when there is none.
 */
public record ChatHistoryPage(List<ChatMessageDto> messages, boolean hasMore, Long nextBefore) {

    public static ChatHistoryPage empty() {
        return new ChatHistoryPage(List.of(), false, null);
    }
}
