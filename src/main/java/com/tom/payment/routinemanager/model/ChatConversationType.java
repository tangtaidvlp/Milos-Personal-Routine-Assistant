package com.tom.payment.routinemanager.model;

/** What a Buddy conversation is about. */
public enum ChatConversationType {
    DEFAULT_ROUTINE,
    DAILY_ROUTINE,
    /** Buddy chat about a day's reflection (F007); uses routineDate. */
    DAILY_REFLECTION,
    /** Buddy chat on the Personal Target page (F013 US4); one per user, no subject column. */
    PERSONAL_TARGET
}
