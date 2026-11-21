package com.tom.payment.routinemanager.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Buddy chat history settings ({@code app.chat.*}).
 *
 * @param compactionThreshold messages since the last summary before the
 *                            conversation is compacted (min 2)
 * @param historyPageSize     max messages returned per history request (min 1)
 */
@ConfigurationProperties("app.chat")
public record ChatHistoryProperties(Integer compactionThreshold, Integer historyPageSize) {

    public ChatHistoryProperties {
        compactionThreshold = compactionThreshold == null ? 20 : compactionThreshold;
        historyPageSize = historyPageSize == null ? 20 : historyPageSize;
        if (compactionThreshold < 2) {
            throw new IllegalArgumentException("app.chat.compaction-threshold must be >= 2");
        }
        if (historyPageSize < 1) {
            throw new IllegalArgumentException("app.chat.history-page-size must be >= 1");
        }
    }
}
