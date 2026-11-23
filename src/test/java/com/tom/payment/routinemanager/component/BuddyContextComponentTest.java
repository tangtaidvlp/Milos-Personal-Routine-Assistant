package com.tom.payment.routinemanager.component;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalTime;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.tom.payment.routinemanager.funcs.BuddyTone;
import com.tom.payment.routinemanager.funcs.ChatKind;
import com.tom.payment.routinemanager.funcs.TimeOfDay;
import com.tom.payment.routinemanager.service.BuddyCall;
import com.tom.payment.routinemanager.service.BuddyContextService;
import com.tom.payment.routinemanager.support.BaseComponentTest;
import com.tom.payment.routinemanager.support.TestUser;

/**
 * Flow: what the user sets on the Personal Target page, and their time of day, reach Buddy's
 * prompt (epic E001: F013 goals, F014 tone, F015 time and reply length). The prompt is built only
 * for the AI, so it is read directly here, like Buddy's tool methods; the text rules are in the
 * unit tests PersonalGoalPromptTest, BuddyTonePromptTest and TimeOfDayPromptTest.
 */
class BuddyContextComponentTest extends BaseComponentTest {

    @Autowired
    private BuddyContextService buddyContext;

    @Test
    void newUserGetsBalancedAndNoGoals() throws Exception {
        TestUser user = newUser("context");
        String block = buddyContext.promptBlock(user.id(), BuddyCall.DAILY_CHAT, null);
        assertTrue(block.contains("\"" + BuddyTone.BALANCED.displayName() + "\""));
        assertTrue(block.contains("has not set any personal goals"));
        assertFalse(block.contains("for the user (their local time"));
    }

    @Test
    void savedToneAndGoalsReachTheNextPrompt() throws Exception {
        TestUser user = newUser("context");
        mvc.perform(as(user, patch("/api/v1/users/" + user.id() + "/settings"), Map.of("buddyTone", "warm_anchor")))
                .andExpect(status().isOk());
        mvc.perform(as(user, post("/api/v1/users/" + user.id() + "/goals"), Map.of("goal", "Run a 10k by March")))
                .andExpect(status().isCreated());

        String chat = buddyContext.promptBlock(user.id(), BuddyCall.DAILY_CHAT, LocalTime.of(8, 0));
        assertTrue(chat.contains("\"" + BuddyTone.WARM_ANCHOR.displayName() + "\""));
        assertTrue(chat.contains(BuddyTone.WARM_ANCHOR.styleLine()));
        assertTrue(chat.contains("Run a 10k by March"));
        assertTrue(chat.contains("proposeGoal"));
        assertTrue(chat.contains("It is " + TimeOfDay.MORNING.label() + " for the user"));

        String feedback = buddyContext.promptBlock(user.id(), BuddyCall.REFLECTION_FEEDBACK, LocalTime.of(21, 0));
        assertTrue(feedback.contains("Run a 10k by March"));
        assertFalse(feedback.contains("proposeGoal"));
        assertTrue(feedback.contains("It is " + TimeOfDay.EVENING.label() + " for the user"));
    }

    @Test
    void onlyTheTodayChatIsKeptShort() throws Exception {
        TestUser user = newUser("context");
        assertTrue(buddyContext.promptBlock(user.id(), BuddyCall.DAILY_CHAT, null).contains(ChatKind.DAY_TO_DAY.lengthRule()));
        for (BuddyCall deep : new BuddyCall[] {BuddyCall.DEFAULT_ROUTINE_CHAT, BuddyCall.REFLECTION_CHAT, BuddyCall.REFLECTION_FEEDBACK}) {
            String block = buddyContext.promptBlock(user.id(), deep, LocalTime.NOON);
            assertTrue(block.contains(ChatKind.DEEP.lengthRule()), deep.name());
            assertFalse(block.contains(ChatKind.DAY_TO_DAY.lengthRule()), deep.name());
        }
    }
}
