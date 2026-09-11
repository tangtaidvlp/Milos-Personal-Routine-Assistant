package com.tom.payment.routinemanager.api;

import com.tom.payment.routinemanager.dto.ApiError;

import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Keeps error responses on the normal dispatch path so they carry the CORS
 * headers the filter chain added. An error response that loses them surfaces in
 * the browser as "TypeError: Failed to fetch", which is indistinguishable from a
 * CORS misconfiguration and hides the real fault.
 *
 * <p>Extends {@link ResponseEntityExceptionHandler} so Spring MVC's own
 * exceptions keep their correct status codes — a catch-all alone turns a missing
 * route (404) or a validation failure (400) into a 500.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException ex) {
        HttpStatusCode status = ex.getStatusCode();
        return ResponseEntity.status(status)
                .body(ApiError.of(status.value(), reasonFor(status), ex.getReason()));
    }

    /**
     * Declared explicitly so the catch-all below does not turn an authorization
     * failure into a 500.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of(HttpStatus.FORBIDDEN.value(), "Forbidden", "Access denied"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        // Deliberately not echoing ex.getMessage() — it can carry stack detail
        // or query fragments.
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "Internal Server Error", "Unexpected error"));
    }

    /** Gives the framework's own exceptions the same body shape as ours. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
                                                             HttpHeaders headers,
                                                             HttpStatusCode statusCode,
                                                             WebRequest request) {
        ApiError error = ApiError.of(statusCode.value(), reasonFor(statusCode), ex.getMessage());
        return super.handleExceptionInternal(ex, error, headers, statusCode, request);
    }

    private String reasonFor(HttpStatusCode status) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        return resolved != null ? resolved.getReasonPhrase() : "Error";
    }
}
