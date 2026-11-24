package com.tom.payment.routinemanager.funcs;

/**
 * Buddy's verdict on a goal being saved (specs/F013 US5, data-model §6): accepted, or refused
 * with a reason and an optional suggested version. Never stored.
 */
public record GoalReview(boolean accepted, String reason, GoalRules.Normalized suggestion) {

    public static final GoalReview ACCEPTED = new GoalReview(true, null, null);
}
