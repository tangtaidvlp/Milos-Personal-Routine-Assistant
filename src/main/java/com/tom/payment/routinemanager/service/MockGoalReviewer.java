package com.tom.payment.routinemanager.service;

import java.util.Locale;
import java.util.Objects;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.tom.payment.routinemanager.funcs.GoalReview;
import com.tom.payment.routinemanager.funcs.GoalReviewRules;
import com.tom.payment.routinemanager.funcs.GoalRules;

/**
 * Used when AI is off: accepts every goal except text containing "impossible", so tests and the
 * e2e journey can show a refusal without Gemini (specs/F013 research R12).
 */
@Service
@ConditionalOnProperty(name = "spring.ai.active", havingValue = "false", matchIfMissing = true)
public class MockGoalReviewer implements GoalReviewer {

    public static final String REASON = "That sounds very hard to keep up. A smaller step is easier to stick with.";
    public static final String SUGGESTION = "Take one small step toward it each day";

    @Override
    public GoalReview review(GoalRules.Normalized goal) {
        String text = (goal.goal() + " " + Objects.toString(goal.howToHelp(), "")).toLowerCase(Locale.ROOT);
        if (text.contains("impossible")) {
            return GoalReviewRules.clean(false, REASON, SUGGESTION, null);
        }
        return GoalReview.ACCEPTED;
    }
}
