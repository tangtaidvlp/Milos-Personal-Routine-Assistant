package com.tom.payment.routinemanager.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.tom.payment.routinemanager.funcs.GoalRules;
import com.tom.payment.routinemanager.model.PersonalGoal;
import com.tom.payment.routinemanager.repository.PersonalGoalRepository;
import com.tom.payment.routinemanager.service.PersonalGoalService;
import com.tom.payment.routinemanager.support.BaseComponentTest;
import com.tom.payment.routinemanager.support.TestUser;

/**
 * Flow: a user keeps up to 3 personal goals on the Personal Target page (F013 US1,
 * contracts/goals-api.md). Text rules' edge cases are in unit/GoalRulesTest.
 */
class PersonalGoalComponentTest extends BaseComponentTest {

    @Autowired
    private PersonalGoalRepository goalRepository;

    @Test
    void newUserHasNoGoalsAndALimitOfThree() throws Exception {
        TestUser user = newUser("goals");
        JsonNode body = read(mvc.perform(as(user, get(goalsUrl(user.id())))).andExpect(status().isOk()));
        assertEquals(0, body.get("goals").size());
        assertEquals(3, body.get("limit").asInt());
    }

    @Test
    void createdGoalIsTrimmedStoredAndListed() throws Exception {
        TestUser user = newUser("goals");
        JsonNode created = read(createGoal(user, "  Run a 10k by March  ", "  Keep me training 3x a week ")
                .andExpect(status().isCreated()));
        assertEquals("Run a 10k by March", created.get("goal").asText());
        assertEquals("Keep me training 3x a week", created.get("howToHelp").asText());

        PersonalGoal stored = goalRepository.findById(UUID.fromString(created.get("id").asText())).orElseThrow();
        assertEquals(user.id(), stored.getUserId());
        assertEquals("Run a 10k by March", stored.getGoal());

        clock.advance(Duration.ofMinutes(1)); // the test clock is fixed; goals are listed by creation time
        JsonNode noHelp = read(createGoal(user, "Sleep by 11 PM", null).andExpect(status().isCreated()));
        assertTrue(noHelp.get("howToHelp").isNull());
        JsonNode list = read(mvc.perform(as(user, get(goalsUrl(user.id())))).andExpect(status().isOk()));
        assertEquals(2, list.get("goals").size());
        assertEquals("Run a 10k by March", list.at("/goals/0/goal").asText());
        assertEquals("Sleep by 11 PM", list.at("/goals/1/goal").asText());
    }

    @Test
    void fourthGoalIsRefused() throws Exception {
        TestUser user = newUser("goals");
        for (int i = 1; i <= 3; i++) {
            createGoal(user, "Goal " + i, null).andExpect(status().isCreated());
        }
        JsonNode refused = read(createGoal(user, "Goal 4", null).andExpect(error(HttpStatus.CONFLICT)));
        assertEquals(GoalRules.LIMIT_REACHED, refused.get("message").asText());
        assertEquals(3, goalRepository.countByUserId(user.id()));
    }

    @Test
    void invalidTextIsRefusedAndNothingIsStored() throws Exception {
        TestUser user = newUser("goals");
        JsonNode blank = read(createGoal(user, "   ", "help").andExpect(error(HttpStatus.BAD_REQUEST)));
        assertEquals(GoalRules.GOAL_REQUIRED, blank.get("message").asText());
        JsonNode tooLong = read(createGoal(user, "g".repeat(151), null).andExpect(error(HttpStatus.BAD_REQUEST)));
        assertEquals(GoalRules.GOAL_TOO_LONG, tooLong.get("message").asText());
        assertEquals(0, goalRepository.countByUserId(user.id()));
    }

    @Test
    void updateReplacesBothTextsAndDeleteRemovesTheGoal() throws Exception {
        TestUser user = newUser("goals");
        JsonNode created = read(createGoal(user, "Run a 10k", "Keep me training").andExpect(status().isCreated()));
        String id = created.get("id").asText();

        clock.advance(Duration.ofMinutes(5));
        JsonNode updated = read(mvc.perform(as(user, put(goalUrl(user.id(), id)), goalBody("Run a half marathon", " ")))
                .andExpect(status().isOk()));
        assertEquals("Run a half marathon", updated.get("goal").asText());
        assertTrue(updated.get("howToHelp").isNull());
        assertNotEquals(created.get("updatedAt").asText(), updated.get("updatedAt").asText());
        assertEquals("Run a half marathon", goalRepository.findById(UUID.fromString(id)).orElseThrow().getGoal());

        mvc.perform(as(user, delete(goalUrl(user.id(), id)))).andExpect(status().isNoContent());
        assertTrue(goalRepository.findById(UUID.fromString(id)).isEmpty());
        mvc.perform(as(user, delete(goalUrl(user.id(), id)))).andExpect(error(HttpStatus.NOT_FOUND));
    }

    @Test
    void anotherUsersGoalCannotBeChangedOrDeleted() throws Exception {
        TestUser owner = newUser("goals");
        TestUser other = newUser("goals");
        String id = read(createGoal(owner, "Run a 10k", null).andExpect(status().isCreated())).get("id").asText();

        // Through their own path the goal id is simply not theirs.
        JsonNode notFound = read(mvc.perform(as(other, put(goalUrl(other.id(), id)), goalBody("Mine now", null)))
                .andExpect(error(HttpStatus.NOT_FOUND)));
        assertEquals(PersonalGoalService.NOT_FOUND, notFound.get("message").asText());
        mvc.perform(as(other, delete(goalUrl(other.id(), id)))).andExpect(error(HttpStatus.NOT_FOUND));

        // Through the owner's path they are not the signed-in user.
        mvc.perform(as(other, get(goalsUrl(owner.id())))).andExpect(error(HttpStatus.FORBIDDEN));
        mvc.perform(as(other, delete(goalUrl(owner.id(), id)))).andExpect(error(HttpStatus.FORBIDDEN));

        assertEquals("Run a 10k", goalRepository.findById(UUID.fromString(id)).orElseThrow().getGoal());
    }

    @Test
    void unknownUserIsNotFoundAndNoTokenIsNotAuthenticated() throws Exception {
        TestUser user = newUser("goals");
        mvc.perform(as(user, get(goalsUrl(UUID.randomUUID())))).andExpect(error(HttpStatus.NOT_FOUND));
        mvc.perform(get(goalsUrl(user.id()))).andExpect(error(HttpStatus.UNAUTHORIZED));
    }

    private ResultActions createGoal(TestUser user, String goal, String howToHelp) throws Exception {
        return mvc.perform(as(user, post(goalsUrl(user.id())), goalBody(goal, howToHelp)));
    }

    private static Map<String, Object> goalBody(String goal, String howToHelp) {
        Map<String, Object> body = new HashMap<>();
        body.put("goal", goal);
        body.put("howToHelp", howToHelp);
        return body;
    }

    private static String goalsUrl(UUID userId) {
        return "/api/v1/users/" + userId + "/goals";
    }

    private static String goalUrl(UUID userId, String goalId) {
        return goalsUrl(userId) + "/" + goalId;
    }
}
