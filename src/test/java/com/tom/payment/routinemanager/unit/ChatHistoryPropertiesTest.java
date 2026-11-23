package com.tom.payment.routinemanager.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.tom.payment.routinemanager.config.ChatHistoryProperties;

/** Rules: chat history page size, and when a conversation is due for compaction (F004). */
class ChatHistoryPropertiesTest {

    private final ChatHistoryProperties properties = new ChatHistoryProperties(4, 20);

    @Test
    void defaultsAreTwentyAndTwenty() {
        ChatHistoryProperties defaults = new ChatHistoryProperties(null, null);
        assertEquals(20, defaults.compactionThreshold());
        assertEquals(20, defaults.historyPageSize());
    }

    @Test
    void rejectsSettingsThatCouldNeverWork() {
        assertThrows(IllegalArgumentException.class, () -> new ChatHistoryProperties(1, 20));
        assertThrows(IllegalArgumentException.class, () -> new ChatHistoryProperties(4, 0));
    }

    @Test
    void pageSizeIsTheRequestedLimitClampedToOneAndTheMaximum() {
        assertEquals(20, properties.pageSizeFor(null));
        assertEquals(1, properties.pageSizeFor(0));
        assertEquals(1, properties.pageSizeFor(-5));
        assertEquals(5, properties.pageSizeFor(5));
        assertEquals(20, properties.pageSizeFor(20));
        assertEquals(20, properties.pageSizeFor(500));
    }

    @Test
    void compactionIsDueFromTheThresholdOnwards() {
        assertFalse(properties.compactionDue(0));
        assertFalse(properties.compactionDue(3));
        assertTrue(properties.compactionDue(4));
        assertTrue(properties.compactionDue(10));
    }
}
