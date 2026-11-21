package com.tom.payment.routinemanager.service.chat;

import java.util.List;

import com.tom.payment.routinemanager.model.ChatMessage;

/** Condenses a conversation into Buddy-only context (F004 compaction). */
public interface ChatSummarizer {

    /**
     * @param previousSummary the current summary, or null
     * @param messages        messages since that summary, oldest first
     * @return the new cumulative summary
     */
    String summarize(String previousSummary, List<ChatMessage> messages);
}
