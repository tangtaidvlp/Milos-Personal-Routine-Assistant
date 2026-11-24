package com.tom.payment.routinemanager.dto;

import java.time.LocalDate;

/** Buddy chat about a day's reflection; {@code localTime} is the user's "HH:mm" (F015, optional). */
public record ReflectionChatRequest(LocalDate date, String message, String localTime) {}
