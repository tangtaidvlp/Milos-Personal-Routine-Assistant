package com.tom.payment.routinemanager.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.tom.payment.routinemanager.funcs.GoalRules;

/** Rule: what a valid personal goal is (F013 data-model §2). */
class GoalRulesTest {

    @Test
    void goalIsRequired() {
        assertRefused(null, null, GoalRules.GOAL_REQUIRED);
        assertRefused("", null, GoalRules.GOAL_REQUIRED);
        assertRefused("   \n\t ", "help", GoalRules.GOAL_REQUIRED);
    }

    @Test
    void goalMayHaveUpTo150CharactersAfterTrimming() {
        String max = "g".repeat(150);
        assertEquals(max, GoalRules.normalize("  " + max + "  ", null).goal());
        assertRefused("g".repeat(151), null, GoalRules.GOAL_TOO_LONG);
    }

    @Test
    void helpIsOptionalAndBlankBecomesNull() {
        assertNull(GoalRules.normalize("Run a 10k", null).howToHelp());
        assertNull(GoalRules.normalize("Run a 10k", "").howToHelp());
        assertNull(GoalRules.normalize("Run a 10k", "   ").howToHelp());
    }

    @Test
    void helpMayHaveUpTo500CharactersAfterTrimming() {
        String max = "h".repeat(500);
        assertEquals(max, GoalRules.normalize("Run", " " + max + " ").howToHelp());
        assertRefused("Run", "h".repeat(501), GoalRules.HELP_TOO_LONG);
    }

    @Test
    void bothTextsAreTrimmed() {
        GoalRules.Normalized goal = GoalRules.normalize("  Run a 10k by March ", "\n Keep me training 3x a week  ");
        assertEquals("Run a 10k by March", goal.goal());
        assertEquals("Keep me training 3x a week", goal.howToHelp());
    }

    private static void assertRefused(String goal, String help, String message) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> GoalRules.normalize(goal, help));
        assertEquals(message, e.getMessage());
    }
}
