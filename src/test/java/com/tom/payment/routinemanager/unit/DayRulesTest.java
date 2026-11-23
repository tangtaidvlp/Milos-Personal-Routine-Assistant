package com.tom.payment.routinemanager.unit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.tom.payment.routinemanager.funcs.DayRules;

/** Rule: past days are frozen for schedule changes; future days can't be reflected on. */
class DayRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 18);

    @Test
    void yesterdayIsPastNotFuture() {
        assertTrue(DayRules.isPast(TODAY.minusDays(1), TODAY));
        assertFalse(DayRules.isFuture(TODAY.minusDays(1), TODAY));
    }

    @Test
    void todayIsNeitherPastNorFuture() {
        assertFalse(DayRules.isPast(TODAY, TODAY));
        assertFalse(DayRules.isFuture(TODAY, TODAY));
    }

    @Test
    void tomorrowIsFutureNotPast() {
        assertTrue(DayRules.isFuture(TODAY.plusDays(1), TODAY));
        assertFalse(DayRules.isPast(TODAY.plusDays(1), TODAY));
    }

    @Test
    void worksAcrossTheYearBoundary() {
        LocalDate newYear = LocalDate.of(2027, 1, 1);
        assertTrue(DayRules.isPast(LocalDate.of(2026, 12, 31), newYear));
        assertTrue(DayRules.isFuture(newYear, LocalDate.of(2026, 12, 31)));
    }
}
