package com.tom.payment.routinemanager.unit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.tom.payment.routinemanager.config.AiCallProperties;
import com.tom.payment.routinemanager.service.AiRateLimiter;
import com.tom.payment.routinemanager.support.MutableClock;

/** Rule: a user may make N AI calls a minute and M a day (B010). */
class AiRateLimiterTest {

    private final MutableClock clock = new MutableClock();
    private final AiRateLimiter limiter = new AiRateLimiter(clock,
            new AiCallProperties(null, null, new AiCallProperties.RateLimit(3, 5)));
    private final UUID user = UUID.randomUUID();

    @Test
    void perMinuteLimitRefusesThenReopensAfterTheMinute() {
        use(user, 3);
        expect(user, AiRateLimiter.TOO_FAST);

        clock.advance(Duration.ofSeconds(59));
        expect(user, AiRateLimiter.TOO_FAST);

        clock.advance(Duration.ofSeconds(2));
        assertDoesNotThrow(() -> limiter.acquire(user));
    }

    @Test
    void perDayLimitRefusesUntilTheDayPasses() {
        for (int i = 0; i < 5; i++) {
            use(user, 1);
            clock.advance(Duration.ofMinutes(2));
        }
        expect(user, AiRateLimiter.DAY_LIMIT);

        clock.advance(Duration.ofDays(1));
        assertDoesNotThrow(() -> limiter.acquire(user));
    }

    @Test
    void usersAreIndependent() {
        use(user, 3);
        expect(user, AiRateLimiter.TOO_FAST);
        assertDoesNotThrow(() -> limiter.acquire(UUID.randomUUID()));
    }

    @Test
    void aRefusedCallIsNotCounted() {
        use(user, 3);
        for (int i = 0; i < 10; i++) {
            expect(user, AiRateLimiter.TOO_FAST);
        }
        clock.advance(Duration.ofSeconds(61));
        // Had the refusals counted, the day limit (5) would be hit here.
        use(user, 2);
        expect(user, AiRateLimiter.DAY_LIMIT);
    }

    private void use(UUID id, int calls) {
        for (int i = 0; i < calls; i++) {
            limiter.acquire(id);
        }
    }

    private void expect(UUID id, String message) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> limiter.acquire(id));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, e.getStatusCode());
        assertEquals(message, e.getReason());
    }
}
