package com.tom.payment.routinemanager.dto;

import java.util.UUID;

import com.tom.payment.routinemanager.model.DefaultRoutine;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ChatRequest {
    private DefaultRoutine defaultRoutine;
    private String message;
    /** The user's local time "HH:mm" (F015); optional. */
    private String localTime;
}
