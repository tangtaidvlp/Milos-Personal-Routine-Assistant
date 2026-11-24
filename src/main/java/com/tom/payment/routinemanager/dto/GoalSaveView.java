package com.tom.payment.routinemanager.dto;

import com.tom.payment.routinemanager.service.GoalSaveResult;

/**
 * POST/PUT goal response (specs/F013 contracts/goals-api.md): {@code goal} when saved, or
 * {@code review} when Buddy refused it and nothing was saved.
 */
public record GoalSaveView(GoalView goal, GoalReviewView review) {

    public static GoalSaveView of(GoalSaveResult result) {
        return result.goal() != null
                ? new GoalSaveView(GoalView.of(result.goal()), null)
                : new GoalSaveView(null, GoalReviewView.of(result.refusal()));
    }
}
