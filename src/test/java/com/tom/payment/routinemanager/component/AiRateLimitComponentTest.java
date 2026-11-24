package com.tom.payment.routinemanager.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.tom.payment.routinemanager.service.AiRateLimiter;
import com.tom.payment.routinemanager.support.BaseComponentTest;
import com.tom.payment.routinemanager.support.TestUser;

/**
 * Flow: a user who calls Buddy too often is refused with 429 and the AI is not called (B010).
 * The test profile sets {@code app.ai.rate-limit.per-minute} to 20 and the clock is fixed, so the
 * 21st call of one user is refused. The limit is shared by every AI entry point.
 */
class AiRateLimitComponentTest extends BaseComponentTest {

    private static final int PER_MINUTE = 20; // application-test.yml

    @Test
    void chatOverTheLimitIs429AndNothingIsStored() throws Exception {
        TestUser user = newUser("rate");
        for (int i = 1; i <= PER_MINUTE; i++) {
            chat(user, "m" + i).andExpect(status().isOk());
        }
        clearInvocations(personalTargetAi);

        chat(user, "one too many").andExpect(error(HttpStatus.TOO_MANY_REQUESTS))
                .andExpect(jsonPath("$.message").value(AiRateLimiter.TOO_FAST));

        verify(personalTargetAi, never()).chat(any(), any(), any(), any());
        JsonNode messages = history(user).get("messages"); // newest page
        assertEquals("m" + PER_MINUTE, messages.get(messages.size() - 2).get("content").asText());
    }

    @Test
    void goalSaveOverTheLimitIs429AndOtherUsersAreNotAffected() throws Exception {
        TestUser user = newUser("rate");
        for (int i = 1; i <= PER_MINUTE; i++) {
            chat(user, "m" + i).andExpect(status().isOk());
        }

        mvc.perform(as(user, post("/api/v1/users/" + user.id() + "/goals"),
                Map.of("goal", "Sleep by 11 PM"))).andExpect(error(HttpStatus.TOO_MANY_REQUESTS));

        chat(newUser("rate-other"), "hello").andExpect(status().isOk());
    }

    private ResultActions chat(TestUser user, String message) throws Exception {
        return mvc.perform(as(user, post("/api/ai/chat/personal-target/" + user.id()), Map.of("message", message)));
    }

    private JsonNode history(TestUser user) throws Exception {
        return read(mvc.perform(as(user, get("/api/ai/chat/personal-target/" + user.id() + "/messages")))
                .andExpect(status().isOk()));
    }
}
