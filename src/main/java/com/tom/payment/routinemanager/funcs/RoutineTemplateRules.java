package com.tom.payment.routinemanager.funcs;

import java.time.DayOfWeek;
import java.time.LocalDate;

import com.tom.payment.routinemanager.model.DefaultRoutine.RoutineTypeEnum;

/**
 * Which default routine a new day is copied from (specs/.done.002-onboarding-instruction research R3):
 * Saturday and Sunday use the weekend routine if it has at least one task;
 * every other case uses the weekday (DAILY) routine.
 */
public final class RoutineTemplateRules {

    private RoutineTemplateRules() {
    }

    public static boolean isWeekend(LocalDate date) {
        return date.getDayOfWeek().getValue() >= DayOfWeek.SATURDAY.getValue();
    }

    public static RoutineTypeEnum templateTypeFor(LocalDate date, boolean weekendRoutineHasTasks) {
        return isWeekend(date) && weekendRoutineHasTasks ? RoutineTypeEnum.WEEKEND : RoutineTypeEnum.DAILY;
    }
}
