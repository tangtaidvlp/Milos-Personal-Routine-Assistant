package com.tom.payment.routinemanager.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.tom.payment.routinemanager.funcs.BuddyTone;
import com.tom.payment.routinemanager.funcs.BuddyTonePrompt;

/** Rule: how the chosen tone is given to Buddy, and the rules every tone keeps (F014). */
class BuddyTonePromptTest {

    @ParameterizedTest
    @EnumSource(BuddyTone.class)
    void everyToneHasItsStyleAndTheSharedRules(BuddyTone tone) {
        String block = BuddyTonePrompt.format(tone);
        assertTrue(block.contains("\"" + tone.displayName() + "\""));
        assertTrue(block.contains(tone.styleLine()));
        assertTrue(block.contains("Never use blame, shame or guilt language"));
        assertTrue(block.contains("never the person"));
        assertTrue(block.contains("It never changes what you may do, what data you use, or which actions need the user's confirmation"));
        assertTrue(block.contains("pick Buddy's tone on the Personal Target page"));
    }

    @Test
    void theSixStylesAreDistinct() {
        Set<String> styles = new HashSet<>();
        Arrays.stream(BuddyTone.values()).forEach(tone -> styles.add(tone.styleLine()));
        assertEquals(6, styles.size());
    }

    @Test
    void unknownOrMissingToneIsBalanced() {
        assertEquals(BuddyTone.BALANCED, BuddyTone.fromStored(null));
        assertEquals(BuddyTone.BALANCED, BuddyTone.fromStored("ROBOT"));
        assertEquals(BuddyTone.WARM_ANCHOR, BuddyTone.fromStored(" warm_anchor "));
        assertTrue(BuddyTonePrompt.format(null).contains("\"Balanced\""));
    }
}
