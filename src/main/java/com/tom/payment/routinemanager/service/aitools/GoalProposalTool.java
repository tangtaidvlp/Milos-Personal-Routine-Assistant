package com.tom.payment.routinemanager.service.aitools;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import com.tom.payment.routinemanager.dto.GoalProposalView;
import com.tom.payment.routinemanager.funcs.GoalRules;
import com.tom.payment.routinemanager.model.PersonalGoal;
import com.tom.payment.routinemanager.service.PersonalGoalService;

/**
 * Buddy's only goal tool (specs/F013 research R5, contracts/buddy-chat-goal-proposal.md §3).
 * One instance per chat call, bound to the signed-in user, so the user never comes from the
 * model. It never writes: it records at most one proposal, which the user confirms in the app
 * through the goals API.
 */
public class GoalProposalTool {

    public static final String SHOWN = "Proposal shown to the user. It is saved only if they confirm.";
    public static final String LIMIT = "Not proposed: the user already has " + GoalRules.MAX_GOALS
            + " goals. Ask which goal to replace and propose a change to it.";
    public static final String UNKNOWN_GOAL = "Not proposed: that goal doesn't exist.";
    public static final String ONLY_ONE = "Not proposed: only one goal proposal per reply.";

    private final UUID userId;
    private final PersonalGoalService goals;
    private GoalProposalView proposal;

    public GoalProposalTool(UUID userId, PersonalGoalService goals) {
        this.userId = userId;
        this.goals = goals;
    }

    @Tool(description = "Propose a new personal goal for the user, or a change to one of their goals (pass its id as goalId). "
            + "Nothing is saved: the user confirms the proposal in the app. Use it only when the user wants to set or change a goal. "
            + "Goals cannot be deleted with this tool.")
    public String proposeGoal(
            @ToolParam(description = "The goal itself, at most 150 characters") String goal,
            @ToolParam(required = false, description = "How Buddy should help with this goal, at most 500 characters. "
                    + "When changing a goal, leave it empty to keep the current text.") String howToHelp,
            @ToolParam(required = false, description = "Id of the goal to change. Leave empty to add a new goal.") String goalId) {
        if (proposal != null) {
            return ONLY_ONE;
        }
        return goalId == null || goalId.isBlank() ? proposeNew(goal, howToHelp) : proposeChange(goalId.strip(), goal, howToHelp);
    }

    /** The proposal recorded during this call, if any. */
    public Optional<GoalProposalView> proposal() {
        return Optional.ofNullable(proposal);
    }

    private String proposeNew(String goal, String howToHelp) {
        GoalRules.Normalized value;
        try {
            value = GoalRules.normalize(goal, howToHelp);
        } catch (IllegalArgumentException e) {
            return "Not proposed: " + e.getMessage();
        }
        List<PersonalGoal> existing = goals.list(userId);
        if (existing.size() >= GoalRules.MAX_GOALS) {
            return LIMIT;
        }
        proposal = new GoalProposalView(GoalProposalView.ADD, null, value.goal(), value.howToHelp(), null);
        return SHOWN;
    }

    private String proposeChange(String goalId, String goal, String howToHelp) {
        UUID id;
        try {
            id = UUID.fromString(goalId);
        } catch (IllegalArgumentException e) {
            return UNKNOWN_GOAL;
        }
        Optional<PersonalGoal> current = goals.find(userId, id);
        if (current.isEmpty()) {
            return UNKNOWN_GOAL;
        }
        PersonalGoal now = current.get();
        String help = howToHelp == null || howToHelp.isBlank() ? now.getHowToHelp() : howToHelp;
        GoalRules.Normalized value;
        try {
            value = GoalRules.normalize(goal, help);
        } catch (IllegalArgumentException e) {
            return "Not proposed: " + e.getMessage();
        }
        proposal = new GoalProposalView(GoalProposalView.UPDATE, id, value.goal(), value.howToHelp(),
                new GoalProposalView.Current(now.getGoal(), now.getHowToHelp()));
        return SHOWN;
    }
}
