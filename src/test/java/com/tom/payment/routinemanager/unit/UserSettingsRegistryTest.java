package com.tom.payment.routinemanager.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.tom.payment.routinemanager.service.UserSettingsRegistry;
import com.tom.payment.routinemanager.service.UserSettingsRegistry.SettingDefinition;

/**
 * Rule: which account-setting values are allowed, and their defaults (F006).
 * The read/patch flow is in component/UserSettingsComponentTest.
 */
class UserSettingsRegistryTest {

    private final UserSettingsRegistry registry = new UserSettingsRegistry();

    @Test
    void builtInKeysAndDefaults() {
        assertEquals(List.of("theme", "boardZoom"), registry.keys());
        assertEquals("SYSTEM", registry.effectiveValue("theme", null));
        assertEquals(1.0, registry.effectiveValue("boardZoom", null));
    }

    @Test
    void validValuesAreNormalised() {
        assertEquals("LIGHT", registry.parse("theme", " light "));
        assertEquals("DARK", registry.parse("theme", "dark"));
        assertEquals("1.25", registry.parse("boardZoom", 1.25));
        assertEquals("0.5", registry.parse("boardZoom", 0.5));
        assertEquals("3.0", registry.parse("boardZoom", 3.0));
    }

    static List<Object[]> invalidValues() {
        return List.of(
                new Object[] {"theme", "BLUE"},
                new Object[] {"theme", 3},
                new Object[] {"boardZoom", 0.25},
                new Object[] {"boardZoom", 3.25},
                new Object[] {"boardZoom", 1.1},
                new Object[] {"boardZoom", "1.5"},
                new Object[] {"fontSize", 14});
    }

    @ParameterizedTest(name = "{0} = {1} is refused")
    @MethodSource("invalidValues")
    void invalidValuesAreRefused(String key, Object value) {
        assertThrows(IllegalArgumentException.class, () -> registry.parse(key, value));
    }

    @Test
    void storedValuesAreCheckedWhenRead() {
        assertTrue(registry.isValidStored("boardZoom", "3.0"));
        assertFalse(registry.isValidStored("boardZoom", "banana"));
        assertFalse(registry.isValidStored("fontSize", "14"));
        assertEquals(1.0, registry.effectiveValue("boardZoom", "banana"));
    }

    @Test
    void aNewPreferenceIsOneMoreDefinition() {
        List<SettingDefinition> definitions = new ArrayList<>(UserSettingsRegistry.builtIns());
        definitions.add(new SettingDefinition("compactMode", "false", raw -> {
            if (!(raw instanceof Boolean value)) {
                throw new IllegalArgumentException("compactMode must be true or false");
            }
            return value.toString();
        }, Boolean::parseBoolean));
        UserSettingsRegistry extended = new UserSettingsRegistry(definitions);

        assertEquals(List.of("theme", "boardZoom", "compactMode"), extended.keys());
        assertEquals(false, extended.effectiveValue("compactMode", null));
        assertEquals("true", extended.parse("compactMode", true));
        assertThrows(IllegalArgumentException.class, () -> extended.parse("compactMode", "yes"));
        assertEquals("SYSTEM", extended.effectiveValue("theme", null));
    }
}
