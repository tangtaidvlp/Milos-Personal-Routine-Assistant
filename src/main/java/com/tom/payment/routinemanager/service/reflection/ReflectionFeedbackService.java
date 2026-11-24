package com.tom.payment.routinemanager.service.reflection;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.tom.payment.routinemanager.dto.ReflectionView;
import com.tom.payment.routinemanager.model.User;
import com.tom.payment.routinemanager.service.AiRateLimiter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Buddy's encouraging note for a reflection (FR-015..017). Separate from
 * ReflectionService so the AI tools can depend on ReflectionService without a cycle.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReflectionFeedbackService {

    public static final String AI_UNAVAILABLE = "Buddy is unavailable right now";

    private final ReflectionService reflectionService;
    private final ReflectionAiService reflectionAi;
    private final AiRateLimiter rateLimiter;

    /** On AI failure: 503 and the earlier note is kept. */
    public ReflectionView generate(User user, LocalDate date, LocalTime localTime) {
        ReflectionView view = reflectionService.getView(user, date);
        if (!view.exists()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Reflection not found");
        }
        rateLimiter.acquire(user.getId()); // outside the try: a 429 must not become a 503
        // Taken before the AI call: an edit made while Buddy writes makes the note stale.
        Instant basedOn = Instant.now();
        String note;
        try {
            note = reflectionAi.feedback(user.getId(), view, localTime);
        } catch (RuntimeException e) {
            log.warn("Reflection feedback failed for user {} on {}", user.getId(), date, e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, AI_UNAVAILABLE);
        }
        if (note == null || note.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, AI_UNAVAILABLE);
        }
        return reflectionService.saveFeedback(user, date, note, basedOn);
    }
}
