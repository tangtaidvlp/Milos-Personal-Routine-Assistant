package com.tom.payment.routinemanager.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;

import com.tom.payment.routinemanager.support.BaseComponentTest;
import com.tom.payment.routinemanager.support.TestUser;

import io.jsonwebtoken.Jwts;

/**
 * Connection: Spring Security with the JWT filter. A protected endpoint accepts only
 * a valid token issued by this backend.
 */
class JwtSecurityIntegrationTest extends BaseComponentTest {

    private String settingsUrl(TestUser user) {
        return "/api/v1/users/" + user.id() + "/settings";
    }

    @Test
    void validTokenIsAccepted() throws Exception {
        TestUser user = newUser("jwt");
        mvc.perform(as(user, get(settingsUrl(user)))).andExpect(status().isOk());
    }

    @Test
    void missingTokenIsNotAuthenticated() throws Exception {
        TestUser user = newUser("jwt");
        mvc.perform(get(settingsUrl(user))).andExpect(error(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void nonBearerSchemeIsNotAuthenticated() throws Exception {
        TestUser user = newUser("jwt");
        mvc.perform(get(settingsUrl(user)).header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz"))
                .andExpect(error(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void garbageTokenIsNotAuthenticated() throws Exception {
        TestUser user = newUser("jwt");
        mvc.perform(get(settingsUrl(user)).header(HttpHeaders.AUTHORIZATION, "Bearer garbage"))
                .andExpect(error(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void tamperedTokenIsNotAuthenticated() throws Exception {
        TestUser user = newUser("jwt");
        String token = user.accessToken();
        char last = token.charAt(token.length() - 1);
        String tampered = token.substring(0, token.length() - 1) + (last == 'A' ? 'B' : 'A');
        mvc.perform(get(settingsUrl(user)).header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
                .andExpect(error(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void tokenSignedWithAnotherKeyIsNotAuthenticated() throws Exception {
        TestUser user = newUser("jwt");
        String forged = Jwts.builder()
                .subject(user.id().toString())
                .claim("email", user.email())
                .signWith(Jwts.SIG.HS256.key().build())
                .compact();
        mvc.perform(get(settingsUrl(user)).header(HttpHeaders.AUTHORIZATION, "Bearer " + forged))
                .andExpect(error(HttpStatus.UNAUTHORIZED));
    }
}
