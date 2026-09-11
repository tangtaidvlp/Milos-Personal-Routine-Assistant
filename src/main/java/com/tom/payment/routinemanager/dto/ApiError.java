package com.tom.payment.routinemanager.dto;

import java.time.Instant;

/**
 * Error body returned for every handled exception, so the SPA can branch on
 * {@code status} rather than parsing free-text.
 */
public record ApiError(int status, String error, String message, Instant timestamp) {

    public static ApiError of(int status, String error, String message) {
        return new ApiError(status, error, message, Instant.now());
    }
}
