package com.tom.payment.routinemanager.config;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.tom.payment.routinemanager.model.User;
import com.tom.payment.routinemanager.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Makes sure a {@code {userId}} path variable names an existing user and, when
 * the request is authenticated (sit/prod JWT), that it is the caller.
 * Profiles without auth (local/fast) only get the existence check.
 */
@Component
@RequiredArgsConstructor
public class CurrentUserGuard {

    private final UserRepository userRepository;

    public User requireUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean authenticated = auth != null
                && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken);
        if (authenticated && (user.getEmail() == null || !user.getEmail().equalsIgnoreCase(auth.getName()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        return user;
    }
}
