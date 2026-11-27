package com.tom.payment.routinemanager.funcs;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * The "Right now" block added last to Buddy's system text (specs/.done.F015-context-aware-tone,
 * contracts/buddy-local-time.md): the user's part of the day, which adjusts the chosen tone, and
 * how long replies may be in this kind of conversation.
 */
public final class TimeOfDayPrompt {

    static final String KEEP_TONE = "Keep the user's chosen tone recognisable: adjust only energy and pacing, never what you may do.";
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private TimeOfDayPrompt() {
    }

    /** @param localTime the user's local time, or null when unknown (then only the length rule) */
    public static String format(LocalTime localTime, ChatKind kind) {
        StringBuilder block = new StringBuilder("\n\n## Right now\n");
        if (localTime != null) {
            TimeOfDay window = TimeOfDay.of(localTime);
            block.append("It is ").append(window.label()).append(" for the user (their local time is ")
                    .append(localTime.format(HH_MM)).append("). ").append(window.style()).append('\n')
                    .append(KEEP_TONE).append('\n');
        }
        return block.append(kind.lengthRule()).toString();
    }
}
