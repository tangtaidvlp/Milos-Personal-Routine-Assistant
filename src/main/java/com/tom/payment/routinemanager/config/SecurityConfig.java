package com.tom.payment.routinemanager.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, Environment environment) throws Exception {
        boolean allowAll = Arrays.stream(environment.getActiveProfiles())
            .anyMatch(profile -> profile.equals("local") || profile.equals("fast"));

        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource(environment)))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/v1/auth/**").permitAll()
                .anyRequest().access((authentication, context) ->
                        new org.springframework.security.authorization.AuthorizationDecision(allowAll || authentication.get().isAuthenticated()))
            );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(Environment environment) {
        // Dev/staging boxes get a wildcard origin so the FE's ever-changing
        // local/tunnel address doesn't need to be re-added here on every
        // restart. Any other (i.e. production) profile keeps the strict
        // allowlist.
        boolean allowAllOrigins = Arrays.stream(environment.getActiveProfiles())
            .anyMatch(profile -> profile.equals("local") || profile.equals("fast") || profile.equals("sit"));

        CorsConfiguration configuration = new CorsConfiguration();
        if (allowAllOrigins) {
            // allowCredentials(true) forbids the literal origin "*" — patterns
            // reflect the actual request origin back instead, which satisfies
            // the browser's credentialed-CORS requirement while still being
            // permissive.
            configuration.setAllowedOriginPatterns(List.of("*"));
        } else {
            configuration.setAllowedOrigins(List.of("http://localhost:5173", "http://47.130.132.148:4173"));
        }
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
