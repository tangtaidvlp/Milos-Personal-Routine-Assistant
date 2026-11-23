package com.tom.payment.routinemanager.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.tom.payment.routinemanager.funcs.RoutineTemplateRules;
import com.tom.payment.routinemanager.model.DefaultRoutine.RoutineTypeEnum;

/** Rule: which default routine a new day is copied from. Week of Mon 2026-11-16. */
class RoutineTemplateRulesTest {

    @ParameterizedTest
    @ValueSource(strings = {"2026-11-16", "2026-11-17", "2026-11-18", "2026-11-19", "2026-11-20"})
    void weekdaysAlwaysUseTheWeekdayRoutine(String day) {
        LocalDate date = LocalDate.parse(day);
        assertEquals(RoutineTypeEnum.DAILY, RoutineTemplateRules.templateTypeFor(date, true));
        assertEquals(RoutineTypeEnum.DAILY, RoutineTemplateRules.templateTypeFor(date, false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-11-21", "2026-11-22"})
    void weekendsUseTheWeekendRoutineWhenItHasTasks(String day) {
        assertEquals(RoutineTypeEnum.WEEKEND, RoutineTemplateRules.templateTypeFor(LocalDate.parse(day), true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-11-21", "2026-11-22"})
    void weekendsFallBackToTheWeekdayRoutineWhenTheWeekendOneIsEmptyOrMissing(String day) {
        assertEquals(RoutineTypeEnum.DAILY, RoutineTemplateRules.templateTypeFor(LocalDate.parse(day), false));
    }
}
