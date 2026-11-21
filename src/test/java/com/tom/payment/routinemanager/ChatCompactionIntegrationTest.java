package com.tom.payment.routinemanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tom.payment.routinemanager.model.ChatConversation;
import com.tom.payment.routinemanager.model.User;
import com.tom.payment.routinemanager.service.AiChatService;
import com.tom.payment.routinemanager.service.UserService;
import com.tom.payment.routinemanager.service.chat.ChatContext;
import com.tom.payment.routinemanager.service.chat.ChatConversationService;
import com.tom.payment.routinemanager.service.chat.ChatSummarizer;

/** F004 US3: background compaction keeps Buddy's context bounded and never touches stored messages. */
@SpringBootTest(properties = {
        "spring.ai.active=false",
        "app.chat.compaction-threshold=4"
})
@ActiveProfiles("fast")
class ChatCompactionIntegrationTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 21);

    @Autowired private WebApplicationContext context;
    @Autowired private UserService userService;
    @Autowired private ChatConversationService conversations;
    @MockitoSpyBean private AiChatService aiChatService;
    @MockitoSpyBean private ChatSummarizer summarizer;

    private final ObjectMapper json = new ObjectMapper();
    private MockMvc mvc;
    private User user;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        User u = new User();
        String tag = UUID.randomUUID().toString().substring(0, 8);
        u.setUsername("compact-" + tag);
        u.setEmail("compact-" + tag + "@example.test");
        user = userService.createUserAndDefaultRoutine(u);
    }

    @AfterEach
    void resetSpies() {
        reset(aiChatService, summarizer);
    }

    @Test
    void compactsAfterThresholdWithoutChangingVisibleHistory() throws Exception {
        send("I prefer workouts in the morning");
        send("second");

        waitFor(() -> conversation().getSummarizedThroughSeq() == 4, "summary cursor to reach 4");
        ChatConversation c = conversation();
        assertNotNull(c.getSummaryText());
        assertTrue(c.getSummaryText().contains("I prefer workouts in the morning"));

        String body = mvc.perform(get(historyUrl())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode page = json.readTree(body);
        assertEquals(4, page.get("messages").size());
        assertFalse(body.contains("summary"), "summary must never be returned");

        send("third");
        ArgumentCaptor<ChatContext> captor = ArgumentCaptor.forClass(ChatContext.class);
        verify(aiChatService).dailyRoutineChat(eq(user.getId()), any(), eq(DAY), captor.capture(), eq("third"));
        assertNotNull(captor.getValue().summary());
        assertEquals(0, captor.getValue().recentMessages().size(), "everything up to seq 4 is in the summary");

        send("fourth");
        verify(aiChatService).dailyRoutineChat(eq(user.getId()), any(), eq(DAY), captor.capture(), eq("fourth"));
        assertEquals(2, captor.getValue().recentMessages().size());
    }

    @Test
    void summariserFailureKeepsChatWorkingAndRetries() throws Exception {
        doThrow(new RuntimeException("summariser down")).when(summarizer).summarize(any(), anyList());

        send("one");
        send("two");
        waitFor(() -> mockitoCalls() >= 1, "first compaction attempt");
        Thread.sleep(200);
        assertEquals(0, conversation().getSummarizedThroughSeq());
        assertNull(conversation().getSummaryText());

        send("three");
        waitFor(() -> mockitoCalls() >= 2, "retry after the next exchange");
        assertEquals(0, conversation().getSummarizedThroughSeq());
        verify(summarizer, atLeast(2)).summarize(any(), anyList());

        String body = mvc.perform(get(historyUrl())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(6, json.readTree(body).get("messages").size());
    }

    // ---------- helpers ----------

    private int mockitoCalls() {
        return org.mockito.Mockito.mockingDetails(summarizer).getInvocations().size();
    }

    private ChatConversation conversation() {
        return conversations.findDailyConversation(user.getId(), DAY).orElseThrow();
    }

    private void send(String message) throws Exception {
        mvc.perform(post("/api/ai/chat/daily-routine/" + user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("date", DAY.toString(), "message", message))))
                .andExpect(status().isOk());
    }

    private String historyUrl() {
        return "/api/ai/chat/daily-routine/" + user.getId() + "/" + DAY + "/messages";
    }

    private static void waitFor(BooleanSupplier condition, String what) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        fail("Timed out waiting for " + what);
    }
}
