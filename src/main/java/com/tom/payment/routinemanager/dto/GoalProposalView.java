package com.tom.payment.routinemanager.dto;

import java.util.UUID;

/**
 * A goal Buddy proposes in chat (specs/F013 contracts/buddy-chat-goal-proposal.md §1). Not
 * stored: the user confirms it through the goals API, or it is gone.
 *
 * @param action  {@code ADD} or {@code UPDATE}
 * @param goalId  the goal to change (UPDATE only)
 * @param current the goal as it is now (UPDATE only), read from the database
 */
public record GoalProposalView(String action, UUID goalId, String goal, String howToHelp, Current current) {

    public static final String ADD = "ADD";
    public static final String UPDATE = "UPDATE";

    public record Current(String goal, String howToHelp) {
    }
}
