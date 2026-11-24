package com.tom.payment.routinemanager.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.tom.payment.routinemanager.config.AiCallProperties;

/**
 * Per-user limit on user-triggered AI calls (B010), in memory: one backend instance, so no shared store.
 * Call {@link #acquire} right before the AI call; over the limit it throws 429 and records nothing.
 */
@Component
public class AiRateLimiter {

    public static final String TOO_FAST = "You're messaging Buddy too fast. Please wait a minute and try again.";
    public static final String DAY_LIMIT = "You've reached today's limit for Buddy. Please try again later.";

    private static final Duration MINUTE = Duration.ofMinutes(1);
    private static final Duration DAY = Duration.ofDays(1);

    private final Clock clock;
    private final int perMinute;
    private final int perDay;
    private final ConcurrentHashMap<UUID, Deque<Instant>> calls = new ConcurrentHashMap<>();

    public AiRateLimiter(Clock clock, AiCallProperties props) {
        this.clock = clock;
        this.perMinute = props.rateLimit().perMinute();
        this.perDay = props.rateLimit().perDay();
    }

    public void acquire(UUID userId) {
        Instant now = clock.instant();
        Deque<Instant> times = calls.computeIfAbsent(userId, id -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && !times.peekFirst().isAfter(now.minus(DAY))) {
                times.pollFirst();
            }
            if (times.size() >= perDay) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, DAY_LIMIT);
            }
            Instant minuteAgo = now.minus(MINUTE);
            long lastMinute = times.stream().filter(t -> t.isAfter(minuteAgo)).count();
            if (lastMinute >= perMinute) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, TOO_FAST);
            }
            times.addLast(now);
        }
    }
}
