package com.tom.payment.routinemanager.api;

import java.util.Map;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tom.payment.routinemanager.config.CurrentUserGuard;
import com.tom.payment.routinemanager.dto.UserProfileView;
import com.tom.payment.routinemanager.dto.UserSettingsView;
import com.tom.payment.routinemanager.model.User;
import com.tom.payment.routinemanager.service.UserSettingsService;

import lombok.RequiredArgsConstructor;

/**
 * Account-level UI settings and the profile view (specs/F006, contracts/settings-api.md).
 * {@link CurrentUserGuard}: 404 for an unknown user, 403 when the signed-in user is someone else.
 */
@RestController
@RequestMapping("/api/v1/users/{userId}")
@RequiredArgsConstructor
public class UserSettingsController {

    private final CurrentUserGuard currentUserGuard;
    private final UserSettingsService settingsService;

    @GetMapping("/settings")
    public UserSettingsView getSettings(@PathVariable UUID userId) {
        User user = currentUserGuard.requireUser(userId);
        return settingsService.getSettings(user);
    }

    @PatchMapping("/settings")
    public UserSettingsView updateSettings(@PathVariable UUID userId, @RequestBody Map<String, Object> patch) {
        User user = currentUserGuard.requireUser(userId);
        return settingsService.updateSettings(user, patch);
    }

    @GetMapping("/profile")
    public UserProfileView getProfile(@PathVariable UUID userId) {
        User user = currentUserGuard.requireUser(userId);
        return new UserProfileView(user.getId(), user.getUsername(), user.getEmail(), UserProfileView.Plan.FREE);
    }
}
