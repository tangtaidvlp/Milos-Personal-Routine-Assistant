package com.tom.payment.routinemanager.dto;

import com.tom.payment.routinemanager.funcs.GoalReview;

/** Why Buddy refused a goal, and a suggested version (specs/F013 contracts/goals-api.md). */
public record GoalReviewView(String reason, Suggestion suggestion) {

    public record Suggestion(String goal, String howToHelp) {
    }

    public static GoalReviewView of(GoalReview review) {
        Suggestion suggestion = review.suggestion() == null
                ? null
                : new Suggestion(review.suggestion().goal(), review.suggestion().howToHelp());
        return new GoalReviewView(review.reason(), suggestion);
    }
}
