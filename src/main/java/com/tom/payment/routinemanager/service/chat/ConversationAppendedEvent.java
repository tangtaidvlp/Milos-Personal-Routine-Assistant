package com.tom.payment.routinemanager.service.chat;

import java.util.UUID;

/** Published after an exchange is stored; drives background compaction. */
public record ConversationAppendedEvent(UUID conversationId) {
}
