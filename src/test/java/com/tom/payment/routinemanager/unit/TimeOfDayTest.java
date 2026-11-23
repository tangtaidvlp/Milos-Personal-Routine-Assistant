package com.tom.payment.routinemanager.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.tom.payment.routinemanager.funcs.TimeOfDay;

/** Rule: the user's part of the day and the "HH:mm" they send (F015). */
class TimeOfDayTest {

    @ParameterizedTest(name = "{0} is {1}")
    @CsvSource({
            "04:59, EVENING", "05:00, MORNING", "11:59, MORNING", "12:00, MIDDAY",
            "16:59, MIDDAY", "17:00, EVENING", "23:59, EVENING", "00:00, EVENING"})
    void windowsFollowTheBoundaries(String time, TimeOfDay expected) {
        assertEquals(expected, TimeOfDay.of(LocalTime.parse(time)));
    }

    @Test
    void strictHoursAndMinutesAreRead() {
        assertEquals(LocalTime.of(8, 5), TimeOfDay.parse("08:05").orElseThrow());
        assertEquals(LocalTime.of(23, 59), TimeOfDay.parse("23:59").orElseThrow());
        assertEquals(LocalTime.MIDNIGHT, TimeOfDay.parse("00:00").orElseThrow());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"8:05", "24:00", "12:60", "08:05:00", " 08:05", "noon", "25:99"})
    void anythingElseIsUnknown(String value) {
        assertTrue(TimeOfDay.parse(value).isEmpty());
    }
}
