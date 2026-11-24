package com.tom.payment.routinemanager.unit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;

import org.junit.jupiter.api.Test;

import com.tom.payment.routinemanager.funcs.ChatKind;
import com.tom.payment.routinemanager.funcs.TimeOfDay;
import com.tom.payment.routinemanager.funcs.TimeOfDayPrompt;

/** Rule: how the part of the day and the kind of chat are given to Buddy (F015). */
class TimeOfDayPromptTest {

    @Test
    void eachWindowGivesItsStyleAndKeepsTheTone() {
        assertWindow(LocalTime.of(8, 5), TimeOfDay.MORNING, "08:05");
        assertWindow(LocalTime.of(14, 0), TimeOfDay.MIDDAY, "14:00");
        assertWindow(LocalTime.of(21, 30), TimeOfDay.EVENING, "21:30");
        assertWindow(LocalTime.of(1, 15), TimeOfDay.EVENING, "01:15");
    }

    @Test
    void unknownTimeGivesOnlyTheLengthRule() {
        String block = TimeOfDayPrompt.format(null, ChatKind.DEEP);
        assertTrue(block.contains("## Right now"));
        assertTrue(block.contains(ChatKind.DEEP.lengthRule()));
        assertFalse(block.contains("for the user (their local time"));
        assertFalse(block.contains("Keep the user's chosen tone recognisable"));
    }

    @Test
    void dayToDayChatsAreShortAndDeepOnesMayCoach() {
        String quick = TimeOfDayPrompt.format(LocalTime.NOON, ChatKind.DAY_TO_DAY);
        assertTrue(quick.contains("at most 3 sentences unless the user asks for more detail"));
        assertTrue(quick.contains("A plan or task list they asked for doesn't count"));

        String deep = TimeOfDayPrompt.format(LocalTime.NOON, ChatKind.DEEP);
        assertTrue(deep.contains("a longer coaching reply is fine"));
        assertFalse(deep.contains("3 sentences"));
    }

    private static void assertWindow(LocalTime time, TimeOfDay window, String shown) {
        String block = TimeOfDayPrompt.format(time, ChatKind.DAY_TO_DAY);
        assertTrue(block.contains("It is " + window.label() + " for the user (their local time is " + shown + ")"), block);
        assertTrue(block.contains(window.style()));
        assertTrue(block.contains("Keep the user's chosen tone recognisable: adjust only energy and pacing, never what you may do."));
    }
}
