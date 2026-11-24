package com.tom.payment.routinemanager.service;

import com.tom.payment.routinemanager.funcs.GoalReview;
import com.tom.payment.routinemanager.funcs.GoalRules;

/**
 * Buddy's check of a goal before it is saved (specs/F013 US5, research R12): realistic,
 * achievable and safe. Throws when Buddy can't answer; the caller turns that into a 503.
 */
public interface GoalReviewer {

    GoalReview review(GoalRules.Normalized goal);
}
