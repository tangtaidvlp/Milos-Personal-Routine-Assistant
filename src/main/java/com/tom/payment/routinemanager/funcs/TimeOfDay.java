package com.tom.payment.routinemanager.funcs;

import java.time.LocalTime;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The user's part of the day (specs/.done.F015-context-aware-tone, data-model): morning 05:00–11:59,
 * midday 12:00–16:59, evening 17:00–04:59. Buddy adjusts the chosen tone to it.
 */
public enum TimeOfDay {

    MORNING("morning", "Be energetic, crisp and decisive. Focus on clarity and on protecting their first important block of the day."),
    MIDDAY("midday", "Be objective and adaptable. Help them re-plan around disruptions without any guilt."),
    EVENING("evening", "Be slower, reflective and validating. Help them close open items so they can switch off and rest.");

    private static final LocalTime MORNING_START = LocalTime.of(5, 0);
    private static final LocalTime MIDDAY_START = LocalTime.of(12, 0);
    private static final LocalTime EVENING_START = LocalTime.of(17, 0);
    private static final Pattern HH_MM = Pattern.compile("([01]\\d|2[0-3]):([0-5]\\d)");

    private final String label;
    private final String style;

    TimeOfDay(String label, String style) {
        this.label = label;
        this.style = style;
    }

    public String label() {
        return label;
    }

    public String style() {
        return style;
    }

    public static TimeOfDay of(LocalTime time) {
        if (!time.isBefore(MORNING_START) && time.isBefore(MIDDAY_START)) {
            return MORNING;
        }
        if (!time.isBefore(MIDDAY_START) && time.isBefore(EVENING_START)) {
            return MIDDAY;
        }
        return EVENING;
    }

    /** The user's local time from a strict "HH:mm" value; empty for anything else (FR-007). */
    public static Optional<LocalTime> parse(String value) {
        if (value == null || !HH_MM.matcher(value).matches()) {
            return Optional.empty();
        }
        return Optional.of(LocalTime.of(Integer.parseInt(value.substring(0, 2)), Integer.parseInt(value.substring(3))));
    }
}
