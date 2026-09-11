package com.tom.payment.routinemanager.api;

import com.tom.payment.routinemanager.dto.*;
import com.tom.payment.routinemanager.service.AuthenticationService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthenticationController {

    private final AuthenticationService authenticationService;
    private static final String REFRESH_TOKEN_COOKIE = "refresh_token";
    private static final int REFRESH_TOKEN_MAX_AGE = 604800; // 7 days in seconds

    public AuthenticationController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@RequestBody RegisterRequest request) {
        RegisterResponse response = authenticationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @RequestBody LoginRequest request,
            HttpServletResponse response) {

        AuthTokens authTokens = authenticationService.login(request);

        response.addCookie(refreshTokenCookie(authTokens.getRefreshToken(), REFRESH_TOKEN_MAX_AGE));

        return ResponseEntity.ok(authTokens.getLoginResponse());
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(HttpServletRequest request) {
        // Extract refresh token from cookie
        String refreshToken = Arrays.stream(request.getCookies() != null ? request.getCookies() : new Cookie[0])
                .filter(cookie -> REFRESH_TOKEN_COOKIE.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Refresh token not found"));

        RefreshResponse refreshResponse = authenticationService.refresh(refreshToken);
        return ResponseEntity.ok(refreshResponse);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response) {
        
        // Max-age 0 clears it. The cookie jar is keyed by (domain, path, name),
        // so this must be built identically to the one set at login or the
        // delete silently targets nothing and the real cookie survives.
        response.addCookie(refreshTokenCookie("", 0));

        // Optionally revoke tokens in database
        // You'd need to extract user ID from the current session/token
        // authenticationService.logout(userId);

        return ResponseEntity.ok().build();
    }

    /**
     * SameSite=Strict is correct here: the SPA is served from app.<domain> and
     * the API from api.<domain>, so API calls are same-site and the cookie is
     * first-party. Strict also keeps the refresh endpoint closed to CSRF, which
     * matters while csrf() is disabled.
     */
    private Cookie refreshTokenCookie(String value, int maxAge) {
        Cookie cookie = new Cookie(REFRESH_TOKEN_COOKIE, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/api/v1/auth/refresh");
        cookie.setMaxAge(maxAge);
        cookie.setAttribute("SameSite", "Strict");
        return cookie;
    }

}
