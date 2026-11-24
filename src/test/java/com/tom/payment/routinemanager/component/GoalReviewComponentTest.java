package com.tom.payment.routinemanager.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.tom.payment.routinemanager.repository.PersonalGoalRepository;
import com.tom.payment.routinemanager.service.MockGoalReviewer;
import com.tom.payment.routinemanager.service.PersonalGoalService;
import com.tom.payment.routinemanager.support.BaseComponentTest;
import com.tom.payment.routinemanager.support.TestUser;

/**
 * Flow: Buddy checks a goal before it is saved, and a refused goal is never saved (F013 US5,
 * contracts/goals-api.md). The mock reviewer refuses text containing "impossible"; what Buddy
 * really accepts is checked by hand (quickstart F1–F6). Answer clean-up: unit/GoalReviewRulesTest.
 */
class GoalReviewComponentTest extends BaseComponentTest {

    @Autowired
    private PersonalGoalRepository goalRepository;

    @Test
    void refusedGoalIsNotSavedAndComesWithAReasonAndASuggestion() throws Exception {
        TestUser user = newUser("review");

        JsonNode refused = read(create(user, "Do the impossible every day").andExpect(status().isOk()));
        assertTrue(refused.get("goal").isNull());
        assertEquals(MockGoalReviewer.REASON, refused.at("/review/reason").asText());
        assertEquals(MockGoalReviewer.SUGGESTION, refused.at("/review/suggestion/goal").asText());
        assertEquals(0, goalRepository.countByUserId(user.id()));

        JsonNode saved = read(create(user, MockGoalReviewer.SUGGESTION).andExpect(status().isCreated()));
        assertTrue(saved.get("review").isNull());
        assertEquals(MockGoalReviewer.SUGGESTION, saved.at("/goal/goal").asText());
        assertEquals(1, goalRepository.countByUserId(user.id()));
    }

    @Test
    void refusedChangeLeavesTheGoalAsItWasAndAnUnchangedSaveIsNotReviewed() throws Exception {
        TestUser user = newUser("review");
        String id = read(create(user, "Run a 10k").andExpect(status().isCreated())).at("/goal/id").asText();

        JsonNode refused = read(update(user, id, "Run an impossible 10k every day").andExpect(status().isOk()));
        assertTrue(refused.get("goal").isNull());
        assertEquals(MockGoalReviewer.REASON, refused.at("/review/reason").asText());
        assertEquals("Run a 10k", goalRepository.findById(UUID.fromString(id)).orElseThrow().getGoal());

        clearInvocations(goalReviewer);
        update(user, id, "  Run a 10k ").andExpect(status().isOk());
        verify(goalReviewer, never()).review(any());
    }

    @Test
    void whenBuddyCantAnswerNothingIsSaved() throws Exception {
        TestUser user = newUser("review");
        doThrow(new RuntimeException("provider down")).when(goalReviewer).review(any());

        JsonNode error = read(create(user, "Run a 10k").andExpect(error(HttpStatus.SERVICE_UNAVAILABLE)));
        assertEquals(PersonalGoalService.REVIEW_UNAVAILABLE, error.get("message").asText());
        assertEquals(0, goalRepository.countByUserId(user.id()));
    }

    @Test
    void invalidTextAndAFullListNeverReachBuddy() throws Exception {
        TestUser user = newUser("review");
        create(user, "   ").andExpect(error(HttpStatus.BAD_REQUEST));
        verify(goalReviewer, never()).review(any());

        for (int i = 1; i <= 3; i++) {
            create(user, "Goal " + i).andExpect(status().isCreated());
        }
        clearInvocations(goalReviewer);
        create(user, "Goal 4").andExpect(error(HttpStatus.CONFLICT));
        verify(goalReviewer, never()).review(any());
    }

    private ResultActions create(TestUser user, String goal) throws Exception {
        return mvc.perform(as(user, post("/api/v1/users/" + user.id() + "/goals"), body(goal)));
    }

    private ResultActions update(TestUser user, String id, String goal) throws Exception {
        return mvc.perform(as(user, put("/api/v1/users/" + user.id() + "/goals/" + id), body(goal)));
    }

    private static Map<String, Object> body(String goal) {
        Map<String, Object> body = new HashMap<>();
        body.put("goal", goal);
        body.put("howToHelp", null);
        return body;
    }
}
