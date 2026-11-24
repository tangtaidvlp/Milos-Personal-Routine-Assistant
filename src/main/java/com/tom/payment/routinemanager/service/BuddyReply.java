package com.tom.payment.routinemanager.service;

import com.tom.payment.routinemanager.dto.GoalProposalView;

/**
 * One Buddy chat reply (specs/F013 data-model §4). Only {@code text} goes into chat history;
 * the goal proposal is returned once and never stored.
 */
public record BuddyReply(String text, GoalProposalView goalProposal) {

    public static BuddyReply text(String text) {
        return new BuddyReply(text, null);
    }
}
