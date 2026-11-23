package com.tom.payment.routinemanager.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.tom.payment.routinemanager.dto.ApiError;
import com.tom.payment.routinemanager.service.AuthenticationService;

import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Message of the 401 sent when a protected route gets no valid bearer token. */
    public static final String SIGN_IN_REQUIRED = "Please sign in to continue.";

    private final List<String> allowedOrigins;

    public SecurityConfig(@Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   Environment environment,
                                                   CorsConfigurationSource corsConfigurationSource,
                                                   AuthenticationService authenticationService,
                                                   JsonMapper jsonMapper) throws Exception {
        // Dev profiles run without authentication so the FE can be exercised
        // without a token. Every other profile — prod and sit included — gets
        // the JWT filter and a real authorization check.
        boolean allowAll = Arrays.stream(environment.getActiveProfiles())
            .anyMatch(profile -> profile.equals("local") || profile.equals("fast"));

        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .authorizeHttpRequests(auth -> {
                // Preflight carries no cookies and no Authorization header, so it
                // must pass unauthenticated or the real request never fires.
                auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .requestMatchers("/api/v1/auth/**").permitAll();

                if (allowAll) {
                    auth.anyRequest().permitAll();
                } else {
                    auth.anyRequest().authenticated();
                }
            })
            // Without this, a denied anonymous request returns 403. The SPA
            // needs 401 to tell "not logged in, go refresh" apart from
            // "logged in but not allowed", which stays 403. The body is the
            // same ApiError every other error uses (constitution Principle I).
            .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                jsonMapper.writeValue(response.getOutputStream(),
                        ApiError.of(HttpStatus.UNAUTHORIZED.value(), "Unauthorized", SIGN_IN_REQUIRED));
            }));

        if (!allowAll) {
            // Registered here rather than as a @Component: a Filter bean is
            // auto-registered by Boot as a global servlet filter, which runs
            // outside this chain and populates the SecurityContext at the wrong
            // point in the ordering.
            http.addFilterBefore(new JwtAuthenticationFilter(authenticationService),
                                 UsernamePasswordAuthenticationFilter.class);
        }

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Exact origins only, per profile — see app.cors.allowed-origins.
        // allowCredentials(true) forbids a wildcard, and reflecting arbitrary
        // origins back would let any site make credentialed calls to the API.
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        // Every request carrying Authorization is non-simple; without this each
        // one pays an extra preflight round-trip.
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
