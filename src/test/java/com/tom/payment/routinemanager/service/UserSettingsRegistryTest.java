package com.tom.payment.routinemanager.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.tom.payment.routinemanager.dto.UserSettingsView;
import com.tom.payment.routinemanager.model.User;
import com.tom.payment.routinemanager.model.UserSetting;
import com.tom.payment.routinemanager.repository.UserSettingRepository;

/** F006 US3 / FR-016: a new preference is one registry entry; existing ones are untouched. */
class UserSettingsRegistryTest {

    private static UserSettingsRegistry.SettingDefinition compactMode() {
        return new UserSettingsRegistry.SettingDefinition("compactMode", "false", raw -> {
            if (!(raw instanceof Boolean value)) {
                throw new IllegalArgumentException("compactMode must be true or false");
            }
            return value.toString();
        }, Boolean::parseBoolean);
    }

    @Test
    void addedPreferenceAppearsWithItsDefaultAndLeavesTheOthersAlone() {
        List<UserSettingsRegistry.SettingDefinition> definitions = new ArrayList<>(UserSettingsRegistry.builtIns());
        definitions.add(compactMode());
        UserSettingsRegistry registry = new UserSettingsRegistry(definitions);

        UUID userId = UUID.randomUUID();
        UserSettingRepository repository = Mockito.mock(UserSettingRepository.class);
        Mockito.when(repository.findByUserId(userId)).thenReturn(List.of(
                new UserSetting(userId, "theme", "DARK", null),
                new UserSetting(userId, "boardZoom", "1.5", null)));
        User user = new User("u", "u@example.test");
        user.setId(userId);

        UserSettingsView view = new UserSettingsService(repository, registry).getSettings(user);

        assertEquals(Map.of("theme", "DARK", "boardZoom", 1.5, "compactMode", false), view.settings());
        assertEquals(List.of("theme", "boardZoom"), view.customized());
        assertEquals("true", registry.parse("compactMode", true));
        assertThrows(IllegalArgumentException.class, () -> registry.parse("compactMode", "yes"));
    }

    @Test
    void builtInDefaultsAndRules() {
        UserSettingsRegistry registry = new UserSettingsRegistry();
        assertEquals(List.of("theme", "boardZoom"), registry.keys());
        assertEquals("SYSTEM", registry.effectiveValue("theme", null));
        assertEquals(1.0, registry.effectiveValue("boardZoom", null));
        assertEquals("1.25", registry.parse("boardZoom", 1.25));
        assertEquals("LIGHT", registry.parse("theme", " light "));
        assertTrue(registry.isValidStored("boardZoom", "3.0"));
        assertThrows(IllegalArgumentException.class, () -> registry.parse("nope", 1));
    }
}
