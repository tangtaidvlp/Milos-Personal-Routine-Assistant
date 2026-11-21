package com.tom.payment.routinemanager.dto;

import java.util.List;
import java.util.Map;

/**
 * Effective settings of a user (contracts/settings-api.md): every known key with its stored
 * value or default, plus the keys the user has actually saved.
 */
public record UserSettingsView(Map<String, Object> settings, List<String> customized) {
}
