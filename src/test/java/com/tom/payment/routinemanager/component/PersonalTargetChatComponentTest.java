package com.tom.payment.routinemanager.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.tom.payment.routinemanager.dto.GoalProposalView;
import com.tom.payment.routinemanager.service.BuddyReply;
import com.tom.payment.routinemanager.support.BaseComponentTest;
import com.tom.payment.routinemanager.support.TestUser;

/**
 * Flow: the user chats with Buddy about their goals on the Personal Target page (F013 US4,
 * contracts/personal-target-chat-api.md). One conversation per user, kept in history; the reply
 * may carry a goal proposal, which is never stored.
 */
class PersonalTargetChatComponentTest extends BaseComponentTest {

    @Test
    void messageIsAnsweredAndKeptInHistory() throws Exception {
        TestUser user = newUser("goals-chat");
        assertEquals(0, history(user).get("messages").size());

        JsonNode reply = read(send(user, Map.of("message", "help me set a sleep goal", "localTime", "21:30"))
                .andExpect(status().isOk()));
        assertTrue(reply.get("reply").asText().contains("help me set a sleep goal"));
        assertTrue(reply.get("goalProposal").isNull());
        verify(personalTargetAi).chat(eq(user.id()), any(), eq("help me set a sleep goal"), eq(LocalTime.of(21, 30)));

        send(user, Map.of("message", "make it 11 PM")).andExpect(status().isOk());
        JsonNode messages = history(user).get("messages");
        assertEquals(4, messages.size());
        assertEquals("help me set a sleep goal", messages.get(0).get("content").asText());
        assertEquals("make it 11 PM", messages.get(2).get("content").asText());
    }

    @Test
    void replyCarriesAProposalButHistoryKeepsOnlyTheText() throws Exception {
        TestUser user = newUser("goals-chat");
        GoalProposalView proposal = new GoalProposalView(GoalProposalView.ADD, null, "Be in bed by 11 PM", null, null);
        doReturn(new BuddyReply("Here is a goal to confirm.", proposal))
                .when(personalTargetAi).chat(any(), any(), anyString(), any());

        JsonNode reply = read(send(user, Map.of("message", "sleep goal please")).andExpect(status().isOk()));
        assertEquals("ADD", reply.at("/goalProposal/action").asText());
        assertEquals("Be in bed by 11 PM", reply.at("/goalProposal/goal").asText());

        JsonNode messages = history(user).get("messages");
        assertEquals("Here is a goal to confirm.", messages.get(1).get("content").asText());
        assertFalse(messages.get(1).has("goalProposal"));
    }

    @Test
    void aiFailureIs503AndNothingIsStored() throws Exception {
        TestUser user = newUser("goals-chat");
        doThrow(new RuntimeException("provider down")).when(personalTargetAi).chat(any(), any(), anyString(), any());

        send(user, Map.of("message", "hello")).andExpect(error(HttpStatus.SERVICE_UNAVAILABLE));
        assertEquals(0, history(user).get("messages").size());
    }

    @Test
    void refusals() throws Exception {
        TestUser user = newUser("goals-chat");
        TestUser other = newUser("goals-chat-other");

        send(user, Map.of("message", "  ")).andExpect(error(HttpStatus.BAD_REQUEST));
        mvc.perform(as(other, post("/api/ai/chat/personal-target/" + user.id()), Map.of("message", "hi")))
                .andExpect(error(HttpStatus.FORBIDDEN));
        mvc.perform(as(other, get("/api/ai/chat/personal-target/" + user.id() + "/messages")))
                .andExpect(error(HttpStatus.FORBIDDEN));
        mvc.perform(as(user, post("/api/ai/chat/personal-target/" + UUID.randomUUID()), Map.of("message", "hi")))
                .andExpect(error(HttpStatus.NOT_FOUND));
    }

    private ResultActions send(TestUser user, Map<String, String> body) throws Exception {
        return mvc.perform(as(user, post("/api/ai/chat/personal-target/" + user.id()), body));
    }

    private JsonNode history(TestUser user) throws Exception {
        return read(mvc.perform(as(user, get("/api/ai/chat/personal-target/" + user.id() + "/messages")))
                .andExpect(status().isOk()));
    }
}
