package com.tom.payment.routinemanager.dto;

import java.time.LocalDate;

/** Buddy chat about a day's reflection. */
public record ReflectionChatRequest(LocalDate date, String message) {}
