package com.tom.payment.routinemanager.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.tom.payment.routinemanager.dto.GoalProposalView;
import com.tom.payment.routinemanager.funcs.GoalRules;
import com.tom.payment.routinemanager.model.PersonalGoal;
import com.tom.payment.routinemanager.repository.PersonalGoalRepository;
import com.tom.payment.routinemanager.service.BuddyReply;
import com.tom.payment.routinemanager.service.PersonalGoalService;
import com.tom.payment.routinemanager.service.aitools.GoalProposalTool;
import com.tom.payment.routinemanager.support.BaseComponentTest;
import com.tom.payment.routinemanager.support.TestUser;

/**
 * Flow: Buddy proposes a goal change in chat and the reply carries it (F013 US3,
 * contracts/buddy-chat-goal-proposal.md). Buddy's tool is called directly, as only the AI calls it;
 * confirming a proposal is the goals API (PersonalGoalComponentTest).
 */
class GoalProposalComponentTest extends BaseComponentTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 18);

    @Autowired
    private PersonalGoalService goalService;

    @Autowired
    private PersonalGoalRepository goalRepository;

    @Test
    void newGoalIsProposedButNotSaved() throws Exception {
        TestUser user = newUser("propose");
        GoalProposalTool tool = new GoalProposalTool(user.id(), goalService);

        assertEquals(GoalProposalTool.SHOWN, tool.proposeGoal("  Be in bed by 11 PM ", "Nudge me at 10:30", null));

        GoalProposalView proposal = tool.proposal().orElseThrow();
        assertEquals(GoalProposalView.ADD, proposal.action());
        assertNull(proposal.goalId());
        assertEquals("Be in bed by 11 PM", proposal.goal());
        assertEquals("Nudge me at 10:30", proposal.howToHelp());
        assertNull(proposal.current());
        assertEquals(0, goalRepository.countByUserId(user.id()));
    }

    @Test
    void changeKeepsTheCurrentHelpWhenNoneIsGivenAndShowsTheCurrentGoal() throws Exception {
        TestUser user = newUser("propose");
        String id = createGoal(user, "Run a 10k", "Keep me training");
        GoalProposalTool tool = new GoalProposalTool(user.id(), goalService);

        assertEquals(GoalProposalTool.SHOWN, tool.proposeGoal("Run a half marathon", " ", id));

        GoalProposalView proposal = tool.proposal().orElseThrow();
        assertEquals(GoalProposalView.UPDATE, proposal.action());
        assertEquals(UUID.fromString(id), proposal.goalId());
        assertEquals("Run a half marathon", proposal.goal());
        assertEquals("Keep me training", proposal.howToHelp());
        assertEquals("Run a 10k", proposal.current().goal());
        assertEquals("Run a 10k", goalRepository.findById(UUID.fromString(id)).orElseThrow().getGoal());
    }

    @Test
    void refusedProposalsRecordNothing() throws Exception {
        TestUser user = newUser("propose");
        TestUser other = newUser("propose");
        String othersGoal = createGoal(other, "Theirs", null);
        GoalProposalTool tool = new GoalProposalTool(user.id(), goalService);

        assertEquals(GoalProposalTool.UNKNOWN_GOAL, tool.proposeGoal("Mine now", null, othersGoal));
        assertEquals(GoalProposalTool.UNKNOWN_GOAL, tool.proposeGoal("Mine now", null, "not-a-uuid"));
        assertEquals("Not proposed: " + GoalRules.GOAL_REQUIRED, tool.proposeGoal("  ", null, null));
        assertEquals("Not proposed: " + GoalRules.GOAL_TOO_LONG, tool.proposeGoal("g".repeat(151), null, null));
        assertTrue(tool.proposal().isEmpty());
        assertEquals("Theirs", goalRepository.findById(UUID.fromString(othersGoal)).orElseThrow().getGoal());
    }

    @Test
    void aFourthGoalIsNotProposedAndOnlyOneProposalFitsAReply() throws Exception {
        TestUser user = newUser("propose");
        for (int i = 1; i <= 3; i++) {
            createGoal(user, "Goal " + i, null);
        }
        GoalProposalTool full = new GoalProposalTool(user.id(), goalService);
        assertEquals(GoalProposalTool.LIMIT, full.proposeGoal("Goal 4", null, null));
        assertTrue(full.proposal().isEmpty());

        List<PersonalGoal> goals = goalService.list(user.id());
        GoalProposalTool tool = new GoalProposalTool(user.id(), goalService);
        assertEquals(GoalProposalTool.SHOWN, tool.proposeGoal("First change", null, goals.get(0).getId().toString()));
        assertEquals(GoalProposalTool.ONLY_ONE, tool.proposeGoal("Second change", null, goals.get(1).getId().toString()));
        assertEquals("First change", tool.proposal().orElseThrow().goal());
    }

    @Test
    void chatReplyCarriesTheProposalButHistoryKeepsOnlyTheText() throws Exception {
        TestUser user = newUser("propose");
        GoalProposalView proposal = new GoalProposalView(GoalProposalView.ADD, null, "Be in bed by 11 PM", null, null);
        doReturn(new BuddyReply("Ready for you to confirm.", proposal))
                .when(aiChatService).dailyRoutineChat(any(), any(), any(), any(), anyString(), any());

        JsonNode reply = read(sendDaily(user, "add a goal: be in bed by 11 PM").andExpect(status().isOk()));
        assertEquals("Ready for you to confirm.", reply.get("reply").asText());
        assertEquals("ADD", reply.at("/goalProposal/action").asText());
        assertEquals("Be in bed by 11 PM", reply.at("/goalProposal/goal").asText());
        assertTrue(reply.at("/goalProposal/goalId").isNull());

        JsonNode history = read(mvc.perform(as(user, get("/api/ai/chat/daily-routine/" + user.id() + "/" + DAY + "/messages")))
                .andExpect(status().isOk()));
        JsonNode messages = history.get("messages");
        assertEquals("Ready for you to confirm.", messages.get(messages.size() - 1).get("content").asText());
        assertEquals(0, goalRepository.countByUserId(user.id()));
    }

    @Test
    void replyWithoutAProposalHasANullProposal() throws Exception {
        TestUser user = newUser("propose");
        JsonNode reply = read(sendDaily(user, "hello").andExpect(status().isOk()));
        assertTrue(reply.has("goalProposal"));
        assertTrue(reply.get("goalProposal").isNull());
    }

    private String createGoal(TestUser user, String goal, String howToHelp) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("goal", goal);
        body.put("howToHelp", howToHelp);
        return read(mvc.perform(as(user, post("/api/v1/users/" + user.id() + "/goals"), body))
                .andExpect(status().isCreated())).at("/goal/id").asText();
    }

    private ResultActions sendDaily(TestUser user, String message) throws Exception {
        return mvc.perform(as(user, post("/api/ai/chat/daily-routine/" + user.id()),
                Map.of("date", DAY.toString(), "message", message)));
    }
}
