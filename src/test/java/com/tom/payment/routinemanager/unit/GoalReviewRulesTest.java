package com.tom.payment.routinemanager.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.tom.payment.routinemanager.funcs.GoalReview;
import com.tom.payment.routinemanager.funcs.GoalReviewRules;
import com.tom.payment.routinemanager.funcs.GoalRules;

/** Rule: how Buddy's answer about a goal is cleaned before the user sees it (F013 US5, research R12). */
class GoalReviewRulesTest {

    @Test
    void acceptedIgnoresEverythingElse() {
        assertSame(GoalReview.ACCEPTED, GoalReviewRules.clean(true, "ignored", "ignored", "ignored"));
    }

    @Test
    void refusalKeepsAValidSuggestionTrimmed() {
        GoalReview review = GoalReviewRules.clean(false, "  Too little sleep isn't safe.  ", "  Sleep 7 hours a night ", "  ");
        assertFalse(review.accepted());
        assertEquals("Too little sleep isn't safe.", review.reason());
        assertEquals(new GoalRules.Normalized("Sleep 7 hours a night", null), review.suggestion());
    }

    @Test
    void blankReasonGetsTheDefaultAndALongOneIsShortened() {
        assertEquals(GoalReviewRules.DEFAULT_REASON, GoalReviewRules.clean(false, " ", null, null).reason());
        assertEquals(GoalReviewRules.DEFAULT_REASON, GoalReviewRules.clean(false, null, null, null).reason());

        String reason = GoalReviewRules.clean(false, "x".repeat(400), null, null).reason();
        assertEquals(GoalReviewRules.REASON_MAX, reason.length());
        assertTrue(reason.endsWith("…"));
    }

    @Test
    void anInvalidSuggestionIsDropped() {
        assertNull(GoalReviewRules.clean(false, "No.", null, null).suggestion());
        assertNull(GoalReviewRules.clean(false, "No.", "   ", "help").suggestion());
        assertNull(GoalReviewRules.clean(false, "No.", "g".repeat(GoalRules.GOAL_MAX + 1), null).suggestion());
        assertNull(GoalReviewRules.clean(false, "No.", "Sleep well", "h".repeat(GoalRules.HELP_MAX + 1)).suggestion());
    }
}
