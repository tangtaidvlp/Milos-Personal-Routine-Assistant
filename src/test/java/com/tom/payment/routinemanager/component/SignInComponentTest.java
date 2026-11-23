package com.tom.payment.routinemanager.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.tom.payment.routinemanager.model.RefreshToken;
import com.tom.payment.routinemanager.repository.RefreshTokenRepository;
import com.tom.payment.routinemanager.repository.UserRepository;
import com.tom.payment.routinemanager.support.BaseComponentTest;

/**
 * Flow: register, sign in, keep the session alive, sign out; and every way sign-in
 * refuses bad input (contracts/sign-in-errors.md, FR-018).
 */
class SignInComponentTest extends BaseComponentTest {

    private static final String INVALID_LOGIN = "Invalid email or password.";
    private static final String SESSION_EXPIRED = "Your session has expired. Please sign in again.";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    // ---------- success paths ----------

    @Test
    void registerCreatesTheAccount() throws Exception {
        String email = uniqueEmail();
        JsonNode body = read(register(email, "Ann", PASSWORD).andExpect(status().isCreated()));
        assertEquals(email, body.get("email").asText());
        assertEquals("Ann", body.get("userName").asText());
        assertEquals(userRepository.findByEmail(email).orElseThrow().getId().toString(), body.get("userId").asText());
    }

    @Test
    void loginReturnsAnAccessTokenAndAnHttpOnlyRefreshCookie() throws Exception {
        String email = registered();
        MvcResult result = login(email, PASSWORD).andExpect(status().isOk()).andReturn();
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertFalse(body.get("accessToken").asText().isBlank());
        assertEquals(userRepository.findByEmail(email).orElseThrow().getId().toString(), body.at("/user/id").asText());

        Cookie cookie = result.getResponse().getCookie("refresh_token");
        assertNotNull(cookie);
        assertTrue(cookie.isHttpOnly());
        assertEquals("/api/v1/auth/refresh", cookie.getPath());
        assertFalse(cookie.getValue().isBlank());
    }

    @Test
    void refreshWithTheCookieGivesANewAccessToken() throws Exception {
        Cookie cookie = refreshCookie(registered());
        JsonNode body = read(refresh(cookie).andExpect(status().isOk()));
        assertFalse(body.get("accessToken").asText().isBlank());
    }

    @Test
    void logoutClearsTheRefreshCookie() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/logout")).andExpect(status().isOk()).andReturn();
        Cookie cookie = result.getResponse().getCookie("refresh_token");
        assertNotNull(cookie);
        assertEquals(0, cookie.getMaxAge());
        assertEquals("/api/v1/auth/refresh", cookie.getPath());
    }

    // ---------- refusals (FR-018) ----------

    @Test
    void loginWithUnknownEmailIsNotAuthenticated() throws Exception {
        JsonNode body = read(login(uniqueEmail(), PASSWORD).andExpect(error(HttpStatus.UNAUTHORIZED)));
        assertEquals(INVALID_LOGIN, body.get("message").asText());
    }

    @Test
    void wrongPasswordLooksExactlyLikeAnUnknownEmail() throws Exception {
        String email = registered();
        JsonNode wrongPassword = read(login(email, "not-the-password").andExpect(error(HttpStatus.UNAUTHORIZED)));
        JsonNode unknownEmail = read(login(uniqueEmail(), PASSWORD).andExpect(error(HttpStatus.UNAUTHORIZED)));
        assertEquals(INVALID_LOGIN, wrongPassword.get("message").asText());
        for (String field : new String[] {"status", "error", "message"}) {
            assertEquals(unknownEmail.get(field), wrongPassword.get(field), field);
        }
        assertFalse(wrongPassword.toString().contains(email));
    }

    @Test
    void registeringAnExistingEmailIsAConflictAndCreatesNoSecondAccount() throws Exception {
        String email = registered();
        JsonNode body = read(register(email, "Someone else", "Other-pass-1").andExpect(error(HttpStatus.CONFLICT)));
        assertEquals("An account with this email already exists.", body.get("message").asText());
        assertEquals(1, userRepository.findAll().stream().filter(u -> email.equals(u.getEmail())).count());
    }

    @Test
    void refreshWithoutCookieIsNotAuthenticated() throws Exception {
        JsonNode body = read(mvc.perform(post("/api/v1/auth/refresh")).andExpect(error(HttpStatus.UNAUTHORIZED)));
        assertEquals("Refresh token not found", body.get("message").asText());
    }

    @Test
    void refreshWithUnknownTokenIsNotAuthenticated() throws Exception {
        expectSessionExpired(refresh(new Cookie("refresh_token", "no-such-token")));
    }

    @Test
    void refreshWithRevokedTokenIsNotAuthenticated() throws Exception {
        Cookie cookie = refreshCookie(registered());
        RefreshToken stored = refreshTokenRepository.findByTokenVal(cookie.getValue()).orElseThrow();
        stored.setIsRevoked(true);
        refreshTokenRepository.save(stored);
        expectSessionExpired(refresh(cookie));
    }

    @Test
    void refreshWithExpiredTokenIsNotAuthenticated() throws Exception {
        Cookie cookie = refreshCookie(registered());
        clock.advance(Duration.ofDays(8));
        expectSessionExpired(refresh(cookie));
    }

    @Test
    void refreshForAUserThatNoLongerExistsIsNotAuthenticated() throws Exception {
        RefreshToken orphan = new RefreshToken();
        orphan.setUserId(UUID.randomUUID());
        orphan.setTokenVal("orphan-" + UUID.randomUUID());
        orphan.setExpiryDate(clock.instant().plus(Duration.ofDays(1)));
        orphan.setIsRevoked(false);
        refreshTokenRepository.save(orphan);
        expectSessionExpired(refresh(new Cookie("refresh_token", orphan.getTokenVal())));
    }

    // ---------- helpers ----------

    private void expectSessionExpired(ResultActions result) throws Exception {
        JsonNode body = read(result.andExpect(error(HttpStatus.UNAUTHORIZED)));
        assertEquals(SESSION_EXPIRED, body.get("message").asText());
        assertFalse(body.toString().contains("Exception"));
    }

    private static String uniqueEmail() {
        return "signin-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test";
    }

    private String registered() throws Exception {
        String email = uniqueEmail();
        register(email, "signin-user", PASSWORD).andExpect(status().isCreated());
        return email;
    }

    private Cookie refreshCookie(String email) throws Exception {
        return login(email, PASSWORD).andExpect(status().isOk()).andReturn().getResponse().getCookie("refresh_token");
    }

    private ResultActions register(String email, String username, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("email", email, "username", username, "password", password))));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("email", email, "password", password))));
    }

    private ResultActions refresh(Cookie cookie) throws Exception {
        return mvc.perform(post("/api/v1/auth/refresh").cookie(cookie));
    }
}
