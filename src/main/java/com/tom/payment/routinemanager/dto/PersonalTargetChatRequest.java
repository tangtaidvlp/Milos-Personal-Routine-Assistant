package com.tom.payment.routinemanager.dto;

/** Buddy chat on the Personal Target page (F013 US4); {@code localTime} is the user's "HH:mm" (F015, optional). */
public record PersonalTargetChatRequest(String message, String localTime) {}
