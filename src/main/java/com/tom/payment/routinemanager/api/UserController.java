package com.tom.payment.routinemanager.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.tom.payment.routinemanager.model.User;
import com.tom.payment.routinemanager.service.UserService;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<User> createUser(@RequestBody User user) {
        User createdUser = userService.createUserAndDefaultDailyRoutine(user);
        return ResponseEntity.ok(createdUser);
    }

    @PostMapping("/{userId}/onboarding/complete")
    public ResponseEntity<User> markOnboardingCompleted(@PathVariable UUID userId) {
        User updatedUser = userService.markOnboardingCompleted(userId);
        return ResponseEntity.ok(updatedUser);
    }
}
