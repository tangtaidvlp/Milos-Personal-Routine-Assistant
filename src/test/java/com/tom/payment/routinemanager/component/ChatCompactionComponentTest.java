package com.tom.payment.routinemanager.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;

import com.tom.payment.routinemanager.model.ChatConversation;
import com.tom.payment.routinemanager.service.chat.ChatContext;
import com.tom.payment.routinemanager.service.chat.ChatConversationService;
import com.tom.payment.routinemanager.support.BaseComponentTest;
import com.tom.payment.routinemanager.support.TestUser;

/**
 * Flow: once a conversation has 20 messages past its summary, Buddy's context is
 * folded into a summary; the visible history never changes (F004 US3). Compaction
 * runs inline in tests (TestSupportConfig), so it has finished when a request returns.
 * The threshold boundary is in unit/ChatHistoryPropertiesTest.
 */
class ChatCompactionComponentTest extends BaseComponentTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 21);
    private static final int EXCHANGES_TO_THRESHOLD = 10; // 2 messages each, default threshold 20

    @Autowired
    private ChatConversationService conversations;

    @Test
    void compactsAtTheThresholdWithoutChangingVisibleHistory() throws Exception {
        TestUser user = newUser("compact");
        send(user, "I prefer workouts in the morning");
        for (int i = 2; i <= EXCHANGES_TO_THRESHOLD; i++) {
            send(user, "message " + i);
        }

        ChatConversation conversation = conversation(user);
        assertEquals(20, conversation.getSummarizedThroughSeq());
        assertNotNull(conversation.getSummaryText());
        assertTrue(conversation.getSummaryText().contains("I prefer workouts in the morning"));

        String body = mvc.perform(as(user, get(historyUrl(user)))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(20, json.readTree(body).get("messages").size());
        assertFalse(body.contains("summary"), "summary must never be returned");

        send(user, "next");
        ArgumentCaptor<ChatContext> captor = ArgumentCaptor.forClass(ChatContext.class);
        verify(aiChatService).dailyRoutineChat(eq(user.id()), any(), eq(DAY), captor.capture(), eq("next"));
        assertNotNull(captor.getValue().summary());
        assertEquals(0, captor.getValue().recentMessages().size(), "everything up to seq 20 is in the summary");

        send(user, "after that");
        verify(aiChatService).dailyRoutineChat(eq(user.id()), any(), eq(DAY), captor.capture(), eq("after that"));
        assertEquals(2, captor.getValue().recentMessages().size());
    }

    @Test
    void summariserFailureKeepsChatWorkingAndRetriesAfterTheNextExchange() throws Exception {
        TestUser user = newUser("compact");
        doThrow(new RuntimeException("summariser down")).when(chatSummarizer).summarize(any(), anyList());

        for (int i = 1; i <= EXCHANGES_TO_THRESHOLD; i++) {
            send(user, "m" + i);
        }
        verify(chatSummarizer, times(1)).summarize(any(), anyList());
        assertEquals(0, conversation(user).getSummarizedThroughSeq());
        assertNull(conversation(user).getSummaryText());

        send(user, "one more");
        verify(chatSummarizer, atLeast(2)).summarize(any(), anyList());
        assertEquals(0, conversation(user).getSummarizedThroughSeq());

        String body = mvc.perform(as(user, get(historyUrl(user)))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(20, json.readTree(body).get("messages").size()); // newest page of 22 stored messages
        assertTrue(json.readTree(body).get("hasMore").asBoolean());
    }

    private void send(TestUser user, String message) throws Exception {
        mvc.perform(as(user, post("/api/ai/chat/daily-routine/" + user.id()),
                        Map.of("date", DAY.toString(), "message", message)))
                .andExpect(status().isOk());
    }

    private ChatConversation conversation(TestUser user) {
        return conversations.findDailyConversation(user.id(), DAY).orElseThrow();
    }

    private String historyUrl(TestUser user) {
        return "/api/ai/chat/daily-routine/" + user.id() + "/" + DAY + "/messages";
    }
}
