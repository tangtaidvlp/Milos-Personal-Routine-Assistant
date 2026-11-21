package com.tom.payment.routinemanager.dto;

import java.time.Instant;

/** Buddy's latest note; {@code stale} once the reflection changed after it was written. */
public record ReflectionFeedbackView(String text, Instant generatedAt, boolean stale) {}
