package com.tom.payment.routinemanager.service;

import com.tom.payment.routinemanager.funcs.BuddyTone;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Every UI preference the server knows, with its default and validation rule
 * (specs/.done.F006-user-profile-configuration-store, data-model §2).
 *
 * <p><b>How to add a preference</b>: add a {@link SettingDefinition} to {@link #builtIns()} and the
 * matching entry to {@code ui-personal-routine-manager/src/features/settings/utils/settingsSchema.ts}.
 * No DDL is needed: values live as rows in {@code user_setting} (FR-016).
 */
@Component
public class UserSettingsRegistry {

    public static final String THEME = "theme";
    public static final String BOARD_ZOOM = "boardZoom";
    /** Buddy's tone (F014): one of {@link BuddyTone}'s ids. */
    public static final String BUDDY_TONE = "buddyTone";

    private static final Set<String> THEMES = Set.of("LIGHT", "DARK", "SYSTEM");
    private static final double ZOOM_MIN = 0.5;
    private static final double ZOOM_MAX = 3.0;
    private static final double ZOOM_STEP = 0.25;

    /**
     * One known setting.
     *
     * @param parse  turns a JSON request value into the canonical stored string, or throws
     *               {@link IllegalArgumentException} with a user-facing reason
     * @param toJson turns a stored string into the JSON value returned by the API
     */
    public record SettingDefinition(String key, String defaultValue,
                                    Function<Object, String> parse,
                                    Function<String, Object> toJson) {
    }

    private final Map<String, SettingDefinition> definitions;

    @Autowired
    public UserSettingsRegistry() {
        this(builtIns());
    }

    /** For a registry with extra definitions (unit tests show how a new preference is added). */
    public UserSettingsRegistry(List<SettingDefinition> definitions) {
        Map<String, SettingDefinition> byKey = new LinkedHashMap<>();
        for (SettingDefinition definition : definitions) {
            byKey.put(definition.key(), definition);
        }
        this.definitions = Collections.unmodifiableMap(byKey);
    }

    /** The built-in preferences. Adding a preference = one more entry here (no DDL). */
    public static List<SettingDefinition> builtIns() {
        List<SettingDefinition> list = new ArrayList<>();
        list.add(new SettingDefinition(THEME, "SYSTEM", UserSettingsRegistry::parseTheme, stored -> stored));
        list.add(new SettingDefinition(BOARD_ZOOM, "1.0", UserSettingsRegistry::parseZoom, Double::parseDouble));
        list.add(new SettingDefinition(BUDDY_TONE, BuddyTone.DEFAULT.name(), UserSettingsRegistry::parseBuddyTone, stored -> stored));
        return list;
    }

    public boolean isKnown(String key) {
        return definitions.containsKey(key);
    }

    /** Known keys in a stable order. */
    public List<String> keys() {
        return List.copyOf(definitions.keySet());
    }

    /** Validates a request value; throws IllegalArgumentException for an unknown key or bad value. */
    public String parse(String key, Object raw) {
        SettingDefinition definition = definitions.get(key);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown setting '" + key + "'");
        }
        return definition.parse().apply(raw);
    }

    /** True when a stored string is still valid for its key. */
    public boolean isValidStored(String key, String stored) {
        SettingDefinition definition = definitions.get(key);
        if (definition == null || stored == null) {
            return false;
        }
        try {
            definition.parse().apply(definition.toJson().apply(stored));
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** The API value for a key: the stored value when valid, otherwise the default. */
    public Object effectiveValue(String key, String storedOrNull) {
        SettingDefinition definition = definitions.get(key);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown setting '" + key + "'");
        }
        String value = isValidStored(key, storedOrNull) ? storedOrNull : definition.defaultValue();
        return definition.toJson().apply(value);
    }

    private static String parseTheme(Object raw) {
        if (!(raw instanceof String text)) {
            throw new IllegalArgumentException("theme must be one of LIGHT, DARK, SYSTEM");
        }
        String upper = text.trim().toUpperCase(Locale.ROOT);
        if (!THEMES.contains(upper)) {
            throw new IllegalArgumentException("theme must be one of LIGHT, DARK, SYSTEM");
        }
        return upper;
    }

    private static String parseBuddyTone(Object raw) {
        String message = "buddyTone must be one of " + String.join(", ", BuddyTone.ids());
        if (!(raw instanceof String text)) {
            throw new IllegalArgumentException(message);
        }
        return BuddyTone.parse(text).orElseThrow(() -> new IllegalArgumentException(message)).name();
    }

    private static String parseZoom(Object raw) {
        if (!(raw instanceof Number number)) {
            throw new IllegalArgumentException("boardZoom must be a number");
        }
        double value = number.doubleValue();
        double steps = value / ZOOM_STEP;
        if (!Double.isFinite(value) || value < ZOOM_MIN || value > ZOOM_MAX || Math.abs(steps - Math.rint(steps)) > 1e-9) {
            throw new IllegalArgumentException("boardZoom must be between 0.5 and 3.0 in steps of 0.25");
        }
        return String.valueOf(Math.rint(steps) * ZOOM_STEP);
    }
}
