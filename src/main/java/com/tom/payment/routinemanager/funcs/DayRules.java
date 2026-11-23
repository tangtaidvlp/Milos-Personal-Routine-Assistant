package com.tom.payment.routinemanager.funcs;

import java.time.LocalDate;

/**
 * Whether a day is in the past or the future, relative to "today" (callers pass
 * {@code LocalDate.now(clock)}). Past days are frozen for Buddy's schedule
 * changes; future days can't be reflected on.
 */
public final class DayRules {

    private DayRules() {
    }

    public static boolean isPast(LocalDate date, LocalDate today) {
        return date.isBefore(today);
    }

    public static boolean isFuture(LocalDate date, LocalDate today) {
        return date.isAfter(today);
    }
}
