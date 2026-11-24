package com.tom.payment.routinemanager.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Limits on AI calls ({@code app.ai.*}, B010).
 *
 * @param timeout    HTTP timeout of one Gemini call
 * @param maxRetries retries after a transient failure (0 = none)
 * @param rateLimit  per-user call limits
 */
@ConfigurationProperties("app.ai")
public record AiCallProperties(Duration timeout, Integer maxRetries, RateLimit rateLimit) {

    /** @param perMinute calls per user in the last minute (min 1) @param perDay calls per user in the last 24 hours (min 1) */
    public record RateLimit(Integer perMinute, Integer perDay) {
        public RateLimit {
            perMinute = perMinute == null ? 10 : perMinute;
            perDay = perDay == null ? 200 : perDay;
            if (perMinute < 1) {
                throw new IllegalArgumentException("app.ai.rate-limit.per-minute must be >= 1");
            }
            if (perDay < 1) {
                throw new IllegalArgumentException("app.ai.rate-limit.per-day must be >= 1");
            }
        }
    }

    public AiCallProperties {
        timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
        maxRetries = maxRetries == null ? 1 : maxRetries;
        rateLimit = rateLimit == null ? new RateLimit(null, null) : rateLimit;
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("app.ai.timeout must be positive");
        }
        if (maxRetries < 0) {
            throw new IllegalArgumentException("app.ai.max-retries must be >= 0");
        }
    }
}
