package com.tom.payment.routinemanager.support;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tom.payment.routinemanager.service.AiChatService;
import com.tom.payment.routinemanager.service.GoalReviewer;
import com.tom.payment.routinemanager.service.PersonalTargetAiService;
import com.tom.payment.routinemanager.service.chat.ChatSummarizer;
import com.tom.payment.routinemanager.service.reflection.ReflectionAiService;

/**
 * Base of every component and integration test (F008, specs/F008-…/contracts/test-harness.md).
 *
 * <p>The whole backend runs for real, including the Spring Security filter chain and
 * the JWT filter. Only the AI provider (mock implementation, spied here) and the clock
 * ({@link MutableClock}) are controlled. All Spring test configuration lives in this
 * class, so every subclass shares one cached application context per run: subclasses
 * must not add Spring test annotations or mock beans (enforced by TestStructureRulesTest).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = TestDatabaseProfiles.class)
@Import(TestSupportConfig.class)
public abstract class BaseComponentTest {

    protected static final String PASSWORD = "Test-pass-123";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected MutableClock clock;

    @MockitoSpyBean
    protected AiChatService aiChatService;

    @MockitoSpyBean
    protected ChatSummarizer chatSummarizer;

    @MockitoSpyBean
    protected ReflectionAiService reflectionAi;

    @MockitoSpyBean
    protected PersonalTargetAiService personalTargetAi;

    @MockitoSpyBean
    protected GoalReviewer goalReviewer;

    protected final ObjectMapper json = new ObjectMapper();

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    /** Registers and signs in a fresh user through the public API. */
    protected TestUser newUser(String prefix) throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        String email = prefix + "-" + tag + "@example.test";
        String username = prefix + "-" + tag;

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", email, "username", username, "password", PASSWORD))))
                .andExpect(status().isCreated());

        JsonNode login = read(mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk()));

        return new TestUser(
                UUID.fromString(login.at("/user/id").asText()),
                email,
                username,
                PASSWORD,
                login.get("accessToken").asText());
    }

    protected TestUser newUser() throws Exception {
        return newUser("user");
    }

    /** Sends the request as the given user (real bearer token). */
    protected static MockHttpServletRequestBuilder as(TestUser user, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.accessToken());
    }

    /** Sends a JSON body as the given user. */
    protected MockHttpServletRequestBuilder as(TestUser user, MockHttpServletRequestBuilder request, Object body)
            throws Exception {
        return as(user, request).contentType(MediaType.APPLICATION_JSON).content(body(body));
    }

    protected String body(Object value) throws Exception {
        return value instanceof String s ? s : json.writeValueAsString(value);
    }

    /**
     * Expects a refusal with this status and the one error body every endpoint returns
     * ({@code ApiError}: exactly {@code status, error, message, timestamp}). Use it for
     * every 4xx/5xx assertion instead of {@code status().isXxx()}; TestStructureRulesTest
     * enforces that. Check the message text separately where the flow promises one.
     */
    protected static ResultMatcher error(HttpStatus expected) {
        return result -> {
            MockHttpServletResponse response = result.getResponse();
            assertEquals(expected.value(), response.getStatus(), "HTTP status");
            String contentType = response.getContentType();
            assertNotNull(contentType, "error response has no body");
            assertTrue(MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(contentType)),
                    "error body is not JSON: " + contentType);

            JsonNode body = ERROR_JSON.readTree(response.getContentAsString());
            Set<String> fields = new TreeSet<>();
            body.fieldNames().forEachRemaining(fields::add);
            assertEquals(ERROR_FIELDS, fields, "error body fields");
            assertEquals(expected.value(), body.get("status").asInt(), "status field");
            assertEquals(expected.getReasonPhrase(), body.get("error").asText(), "error field");
            assertFalse(body.get("message").asText().isBlank(), "message field is blank");
            assertDoesNotThrow(() -> Instant.parse(body.get("timestamp").asText()), "timestamp is not ISO-8601");
        };
    }

    private static final Set<String> ERROR_FIELDS = new TreeSet<>(Set.of("status", "error", "message", "timestamp"));
    private static final ObjectMapper ERROR_JSON = new ObjectMapper();

    protected JsonNode read(ResultActions result) throws Exception {
        String content = result.andReturn().getResponse().getContentAsString();
        return content.isEmpty() ? json.nullNode() : json.readTree(content);
    }
}
