package com.tom.payment.routinemanager.config;

import java.io.IOException;
import java.util.Collections;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import com.tom.payment.routinemanager.service.AuthenticationService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final AuthenticationService authenticationService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String userEmail; // or username

        // 1. Check if the header is missing or doesn't start with "Bearer "
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Extract the token
        jwt = authHeader.substring(7);

        // 3. Extract username/email from JWT
        userEmail = authenticationService.extractUsername(jwt);
        log.debug("User working email   : {}", userEmail);
        // 4. If we have a username and the user isn't authenticated yet
        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            // Validate token using authentication service
            boolean isTokenValid = authenticationService.isTokenValid(jwt);

            if (isTokenValid) {
                // Create an authentication object. Pass roles/authorities in place of Collections.emptyList()
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userEmail, null, Collections.emptyList()
                );

                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // Set the user in Spring Security Context
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        // Continue the filter chain execution
        filterChain.doFilter(request, response);
    }
}