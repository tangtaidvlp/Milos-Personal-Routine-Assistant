package com.tom.payment.routinemanager.service;

import com.tom.payment.routinemanager.funcs.GoalReview;
import com.tom.payment.routinemanager.model.PersonalGoal;

/** A goal save (specs/F013 US5): either the saved goal, or Buddy's refusal and nothing saved. */
public record GoalSaveResult(PersonalGoal goal, GoalReview refusal) {

    public static GoalSaveResult saved(PersonalGoal goal) {
        return new GoalSaveResult(goal, null);
    }

    public static GoalSaveResult refused(GoalReview review) {
        return new GoalSaveResult(null, review);
    }
}
