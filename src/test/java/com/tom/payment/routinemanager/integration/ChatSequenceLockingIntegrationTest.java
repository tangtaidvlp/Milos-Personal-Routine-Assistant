package com.tom.payment.routinemanager.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.tom.payment.routinemanager.support.BaseComponentTest;
import com.tom.payment.routinemanager.support.RequiresPostgres;
import com.tom.payment.routinemanager.support.TestUser;

/**
 * Connection: PostgreSQL row locking. Appending to a conversation locks its row
 * (ChatConversationRepository.findByIdForUpdate), so concurrent messages still get
 * unique, gap-free sequence numbers. H2 can't prove this; it runs with -PtestDb=postgres.
 */
@RequiresPostgres
class ChatSequenceLockingIntegrationTest extends BaseComponentTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 21);
    private static final int SENDERS = 8;

    @Test
    void concurrentMessagesGetUniqueGapFreeSequenceNumbers() throws Exception {
        TestUser user = newUser("locking");
        // The first exchange creates the conversation, so the concurrent ones all append to it.
        send(user, "first");

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(SENDERS);
        try {
            List<Future<?>> results = new ArrayList<>();
            for (int i = 0; i < SENDERS; i++) {
                String message = "parallel " + i;
                results.add(pool.submit(() -> {
                    start.await();
                    send(user, message);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> result : results) {
                result.get();
            }
        } finally {
            pool.shutdownNow();
        }

        JsonNode newest = read(mvc.perform(as(user, get(historyUrl(user)).param("limit", "20")))
                .andExpect(status().isOk()));
        JsonNode messages = newest.get("messages");
        int expected = 2 * (SENDERS + 1);
        assertEquals(expected, messages.size());
        for (int i = 0; i < expected; i++) {
            assertEquals(i + 1, messages.get(i).get("seq").asLong(), "seq at position " + i);
        }
    }

    private void send(TestUser user, String message) throws Exception {
        mvc.perform(as(user, post("/api/ai/chat/daily-routine/" + user.id()),
                        Map.of("date", DAY.toString(), "message", message)))
                .andExpect(status().isOk());
    }

    private String historyUrl(TestUser user) {
        return "/api/ai/chat/daily-routine/" + user.id() + "/" + DAY + "/messages";
    }
}
