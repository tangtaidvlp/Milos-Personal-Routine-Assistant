package com.tom.payment.routinemanager.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.tom.payment.routinemanager.model.ChatConversationType;
import com.tom.payment.routinemanager.repository.ChatConversationRepository;
import com.tom.payment.routinemanager.service.chat.ChatContext;
import com.tom.payment.routinemanager.support.BaseComponentTest;
import com.tom.payment.routinemanager.support.TestUser;

/**
 * Flow: chatting with Buddy stores each exchange per conversation, and the history
 * pages back (F004 US1 + US2). Page-size clamping is in unit/ChatHistoryPropertiesTest.
 */
class ChatHistoryComponentTest extends BaseComponentTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 21);

    @Autowired
    private ChatConversationRepository conversationRepository;

    @Test
    void emptyHistoryBeforeAnyChatAndNoRowCreated() throws Exception {
        TestUser user = newUser("chat");
        UUID routine = weekdayRoutineId(user);
        JsonNode page = history(user, routineHistoryUrl(user.id(), routine));
        assertEquals(0, page.get("messages").size());
        assertFalse(page.get("hasMore").asBoolean());
        assertTrue(page.get("nextBefore").isNull());
        assertTrue(conversationRepository.findByUser_IdAndTypeAndDefaultRoutineId(
                user.id(), ChatConversationType.DEFAULT_ROUTINE, routine).isEmpty());
    }

    @Test
    void defaultRoutineExchangesAreStoredInOrder() throws Exception {
        TestUser user = newUser("chat");
        UUID routine = weekdayRoutineId(user);
        for (int i = 1; i <= 3; i++) {
            sendRoutine(user, routine, "m" + i).andExpect(status().isOk());
        }
        JsonNode messages = history(user, routineHistoryUrl(user.id(), routine)).get("messages");
        assertEquals(6, messages.size());
        for (int i = 0; i < 6; i++) {
            assertEquals(i + 1, messages.get(i).get("seq").asLong());
            assertEquals(i % 2 == 0 ? "user" : "assistant", messages.get(i).get("role").asText());
        }
        assertEquals("m1", messages.get(0).get("content").asText());
        assertEquals("m3", messages.get(4).get("content").asText());
        assertFalse(messages.get(0).has("summary"));
    }

    @Test
    void dailyConversationsAreSeparatePerDateAndFromTheRoutine() throws Exception {
        TestUser user = newUser("chat");
        UUID routine = weekdayRoutineId(user);
        sendRoutine(user, routine, "routine msg").andExpect(status().isOk());
        sendDaily(user, DAY, "day one").andExpect(status().isOk());
        sendDaily(user, DAY, "day two").andExpect(status().isOk());

        JsonNode day = history(user, dailyHistoryUrl(user.id(), DAY)).get("messages");
        assertEquals(4, day.size());
        assertEquals("day one", day.get(0).get("content").asText());
        assertEquals(0, history(user, dailyHistoryUrl(user.id(), DAY.plusDays(1))).get("messages").size());
        assertEquals(2, history(user, routineHistoryUrl(user.id(), routine)).get("messages").size());
    }

    @Test
    void blankMessageIsRefusedAndNotStored() throws Exception {
        TestUser user = newUser("chat");
        UUID routine = weekdayRoutineId(user);
        sendRoutine(user, routine, "   ").andExpect(error(HttpStatus.BAD_REQUEST));
        sendDaily(user, DAY, "").andExpect(error(HttpStatus.BAD_REQUEST));
        assertEquals(0, history(user, routineHistoryUrl(user.id(), routine)).get("messages").size());
        assertEquals(0, history(user, dailyHistoryUrl(user.id(), DAY)).get("messages").size());
    }

    @Test
    void missingDateIsRefused() throws Exception {
        TestUser user = newUser("chat");
        mvc.perform(as(user, post("/api/ai/chat/daily-routine/" + user.id()), Map.of("message", "hi")))
                .andExpect(error(HttpStatus.BAD_REQUEST));
    }

    @Test
    void unknownUserIsNotFound() throws Exception {
        TestUser user = newUser("chat");
        UUID routine = weekdayRoutineId(user);
        UUID unknown = UUID.randomUUID();
        mvc.perform(as(user, get(routineHistoryUrl(unknown, routine)))).andExpect(error(HttpStatus.NOT_FOUND));
        mvc.perform(as(user, get(dailyHistoryUrl(unknown, DAY)))).andExpect(error(HttpStatus.NOT_FOUND));
        mvc.perform(as(user, post("/api/ai/chat/daily-routine/" + unknown),
                        Map.of("date", DAY.toString(), "message", "hi")))
                .andExpect(error(HttpStatus.NOT_FOUND));
    }

    @Test
    void anotherUsersRoutineIsNotFound() throws Exception {
        TestUser user = newUser("chat");
        TestUser other = newUser("chat");
        UUID otherRoutine = weekdayRoutineId(other);
        mvc.perform(as(user, get(routineHistoryUrl(user.id(), otherRoutine)))).andExpect(error(HttpStatus.NOT_FOUND));
        sendRoutine(user, otherRoutine, "hi").andExpect(error(HttpStatus.NOT_FOUND));
    }

    @Test
    void requestWithoutTokenIsNotAuthenticated() throws Exception {
        TestUser user = newUser("chat");
        mvc.perform(get(dailyHistoryUrl(user.id(), DAY))).andExpect(error(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void secondMessageGetsTheFirstExchangeAsContext() throws Exception {
        TestUser user = newUser("chat");
        UUID routine = weekdayRoutineId(user);
        sendRoutine(user, routine, "first").andExpect(status().isOk());
        sendRoutine(user, routine, "second").andExpect(status().isOk());

        ArgumentCaptor<ChatContext> captor = ArgumentCaptor.forClass(ChatContext.class);
        verify(aiChatService).defaultRoutineChat(eq(user.id()), any(), captor.capture(), eq("second"), any());
        ChatContext context = captor.getValue();
        assertNull(context.summary());
        assertEquals(2, context.recentMessages().size());
        assertTrue(context.recentMessages().get(0) instanceof UserMessage);
        assertEquals("first", context.recentMessages().get(0).getText());
        assertTrue(context.recentMessages().get(1) instanceof AssistantMessage);
    }

    @Test
    void failedAiCallStoresNothing() throws Exception {
        TestUser user = newUser("chat");
        doThrow(new RuntimeException("AI down"))
                .when(aiChatService).dailyRoutineChat(any(), any(), any(), any(), anyString(), any());
        sendDaily(user, DAY, "will fail").andExpect(error(HttpStatus.INTERNAL_SERVER_ERROR));
        assertEquals(0, history(user, dailyHistoryUrl(user.id(), DAY)).get("messages").size());
    }

    @Test
    void historyPagesBackwardsWithoutGapsOrDuplicates() throws Exception {
        TestUser user = newUser("chat");
        for (int i = 1; i <= 15; i++) {
            sendDaily(user, DAY, "q" + i).andExpect(status().isOk());
        }
        String url = dailyHistoryUrl(user.id(), DAY);

        JsonNode newest = history(user, url);
        assertEquals(20, newest.get("messages").size());
        assertEquals(11, newest.get("messages").get(0).get("seq").asLong());
        assertEquals(30, newest.get("messages").get(19).get("seq").asLong());
        assertTrue(newest.get("hasMore").asBoolean());
        assertEquals(11, newest.get("nextBefore").asLong());

        JsonNode older = history(user, url + "?before=11");
        assertEquals(10, older.get("messages").size());
        assertEquals(1, older.get("messages").get(0).get("seq").asLong());
        assertFalse(older.get("hasMore").asBoolean());
        assertTrue(older.get("nextBefore").isNull());

        List<Long> all = new ArrayList<>();
        older.get("messages").forEach(m -> all.add(m.get("seq").asLong()));
        newest.get("messages").forEach(m -> all.add(m.get("seq").asLong()));
        for (int i = 0; i < 30; i++) {
            assertEquals(i + 1, all.get(i));
        }

        JsonNode small = history(user, url + "?limit=5&before=11");
        assertEquals(5, small.get("messages").size());
        assertEquals(6, small.get("messages").get(0).get("seq").asLong());
        assertTrue(small.get("hasMore").asBoolean());
    }

    // ---------- helpers ----------

    private UUID weekdayRoutineId(TestUser user) throws Exception {
        JsonNode routine = read(mvc.perform(as(user, get("/api/v1/default-routine/users/" + user.id())))
                .andExpect(status().isOk()));
        return UUID.fromString(routine.get("id").asText());
    }

    private ResultActions sendRoutine(TestUser user, UUID routine, String message) throws Exception {
        return mvc.perform(as(user, post("/api/ai/chat/default-routine/" + user.id()), Map.of(
                "defaultRoutine", Map.of("id", routine.toString()), "message", message)));
    }

    private ResultActions sendDaily(TestUser user, LocalDate date, String message) throws Exception {
        return mvc.perform(as(user, post("/api/ai/chat/daily-routine/" + user.id()),
                Map.of("date", date.toString(), "message", message)));
    }

    private JsonNode history(TestUser user, String url) throws Exception {
        return read(mvc.perform(as(user, get(url))).andExpect(status().isOk()));
    }

    private static String routineHistoryUrl(UUID userId, UUID routineId) {
        return "/api/ai/chat/default-routine/" + userId + "/" + routineId + "/messages";
    }

    private static String dailyHistoryUrl(UUID userId, LocalDate date) {
        return "/api/ai/chat/daily-routine/" + userId + "/" + date + "/messages";
    }
}
