package com.tom.payment.routinemanager.funcs;

/**
 * Cleans Buddy's raw answer about a goal (specs/F013 research R12): the reason is kept short and
 * never empty, and a suggestion is kept only if it would pass {@link GoalRules}.
 */
public final class GoalReviewRules {

    public static final int REASON_MAX = 300;
    public static final String DEFAULT_REASON =
            "This goal doesn't look realistic or safe yet. A smaller, safer version will be easier to keep.";

    private GoalReviewRules() {
    }

    public static GoalReview clean(boolean accepted, String reason, String suggestedGoal, String suggestedHowToHelp) {
        if (accepted) {
            return GoalReview.ACCEPTED;
        }
        String text = reason == null ? "" : reason.strip();
        if (text.isEmpty()) {
            text = DEFAULT_REASON;
        } else if (text.length() > REASON_MAX) {
            text = text.substring(0, REASON_MAX - 1).strip() + "…";
        }
        GoalRules.Normalized suggestion;
        try {
            suggestion = GoalRules.normalize(suggestedGoal, suggestedHowToHelp);
        } catch (IllegalArgumentException invalid) {
            suggestion = null;
        }
        return new GoalReview(false, text, suggestion);
    }
}
