package com.tom.payment.routinemanager.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.tom.payment.routinemanager.dto.UserSettingsView;
import com.tom.payment.routinemanager.model.User;
import com.tom.payment.routinemanager.model.UserSetting;
import com.tom.payment.routinemanager.repository.UserSettingRepository;

import lombok.RequiredArgsConstructor;

/**
 * Reads and saves a user's UI settings (specs/.done.F006-user-profile-configuration-store,
 * contracts/settings-api.md). A missing or invalid stored value reads as the default; rows
 * with unknown keys are ignored.
 */
@Service
@RequiredArgsConstructor
public class UserSettingsService {

    private final UserSettingRepository repository;
    private final UserSettingsRegistry registry;

    @Transactional(readOnly = true)
    public UserSettingsView getSettings(User user) {
        Map<String, String> stored = new LinkedHashMap<>();
        for (UserSetting row : repository.findByUserId(user.getId())) {
            stored.put(row.getSettingKey(), row.getSettingValue());
        }
        Map<String, Object> settings = new LinkedHashMap<>();
        List<String> customized = new ArrayList<>();
        for (String key : registry.keys()) {
            String value = stored.get(key);
            settings.put(key, registry.effectiveValue(key, value));
            if (registry.isValidStored(key, value)) {
                customized.add(key);
            }
        }
        return new UserSettingsView(settings, customized);
    }

    /**
     * Saves the given keys only. Every entry is validated before anything is written, so a bad
     * request changes nothing (400). The last write to a key wins.
     */
    @Transactional
    public UserSettingsView updateSettings(User user, Map<String, Object> patch) {
        if (patch == null || patch.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No settings to update");
        }
        Map<String, String> parsed = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : patch.entrySet()) {
            try {
                parsed.put(entry.getKey(), registry.parse(entry.getKey(), entry.getValue()));
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
            }
        }
        OffsetDateTime now = OffsetDateTime.now();
        for (Map.Entry<String, String> entry : parsed.entrySet()) {
            repository.save(new UserSetting(user.getId(), entry.getKey(), entry.getValue(), now));
        }
        return getSettings(user);
    }
}
