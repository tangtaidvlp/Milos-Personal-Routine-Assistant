package com.tom.payment.routinemanager.config;

import java.time.Duration;

import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.web.client.ResourceAccessException;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;

/**
 * B010: Spring AI's own Gemini client has no HTTP timeout and its default retry runs for minutes.
 * These beans replace them (its client is {@code @ConditionalOnMissingBean}; the chat model takes
 * any {@link RetryTemplate} bean); the SDK's own retry is switched off, so the template is the only retry layer. API-key access only; this app does not use Vertex AI.
 */
@Configuration
@ConditionalOnProperty(name = "spring.ai.active", havingValue = "true")
public class AiClientConfig {

    private static final Duration RETRY_DELAY = Duration.ofSeconds(1);

    @Bean
    public static Client googleGenAiClient(@Value("${spring.ai.google.genai.api-key}") String apiKey, AiCallProperties props) {
        return Client.builder()
                .apiKey(apiKey)
                .httpOptions(HttpOptions.builder()
                        .timeout((int) props.timeout().toMillis())
                        // the SDK retries on its own (5 attempts, backoff) unless told otherwise; 1 attempt = no SDK retry
                        .retryOptions(HttpRetryOptions.builder().attempts(1))
                        .build())
                .build();
    }

    @Bean
    public static RetryTemplate aiRetryTemplate(AiCallProperties props) {
        return new RetryTemplate(RetryPolicy.builder()
                .maxRetries(props.maxRetries())
                .delay(RETRY_DELAY)
                .includes(TransientAiException.class, ResourceAccessException.class)
                .build());
    }
}
