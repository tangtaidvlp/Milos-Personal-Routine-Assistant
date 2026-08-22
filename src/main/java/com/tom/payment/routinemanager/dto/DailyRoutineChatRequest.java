package com.tom.payment.routinemanager.dto;

import java.time.LocalDate;

import com.tom.payment.routinemanager.model.DailyRoutine;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class DailyRoutineChatRequest {
    private DailyRoutine dailyRoutine;
    private LocalDate date;
    private String message;
}
