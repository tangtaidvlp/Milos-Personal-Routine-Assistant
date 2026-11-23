package com.tom.payment.routinemanager.funcs;

/**
 * Rules for a personal goal (specs/F013-personal-goals, data-model §2). Used by the goals API
 * and by Buddy's proposal tool, so both refuse the same input with the same message.
 * Mirrored in the frontend by {@code features/personal-target/utils/goalRules.ts}.
 */
public final class GoalRules {

    public static final int MAX_GOALS = 3;
    public static final int GOAL_MAX = 150;
    public static final int HELP_MAX = 500;

    public static final String GOAL_REQUIRED = "Goal is required.";
    public static final String GOAL_TOO_LONG = "Goal must be " + GOAL_MAX + " characters or fewer.";
    public static final String HELP_TOO_LONG = "How Buddy should help must be " + HELP_MAX + " characters or fewer.";
    public static final String LIMIT_REACHED = "You can have up to " + MAX_GOALS + " goals.";

    /** A goal after trimming; {@code howToHelp} is null when blank. */
    public record Normalized(String goal, String howToHelp) {
    }

    private GoalRules() {
    }

    /**
     * Trims both texts and checks their lengths (counted after trimming).
     *
     * @throws IllegalArgumentException with a user-facing message
     */
    public static Normalized normalize(String goal, String howToHelp) {
        String trimmedGoal = goal == null ? "" : goal.strip();
        if (trimmedGoal.isEmpty()) {
            throw new IllegalArgumentException(GOAL_REQUIRED);
        }
        if (trimmedGoal.length() > GOAL_MAX) {
            throw new IllegalArgumentException(GOAL_TOO_LONG);
        }
        String trimmedHelp = howToHelp == null ? "" : howToHelp.strip();
        if (trimmedHelp.length() > HELP_MAX) {
            throw new IllegalArgumentException(HELP_TOO_LONG);
        }
        return new Normalized(trimmedGoal, trimmedHelp.isEmpty() ? null : trimmedHelp);
    }
}
