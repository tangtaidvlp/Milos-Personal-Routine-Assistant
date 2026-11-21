package com.tom.payment.routinemanager.config;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Task colour palette ({@code app.task-palette.colors}), specs/F005-i-want-color-to-be-persisted data-model §1.
 * List order is the round-robin order. Ids are stored on tasks, so they must never be renamed or removed.
 * Invalid config fails application startup.
 */
@ConfigurationProperties("app.task-palette")
public record TaskPaletteProperties(List<PaletteColor> colors) {

    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9-]*");
    private static final Pattern HEX = Pattern.compile("#[0-9A-Fa-f]{6}");

    public TaskPaletteProperties {
        if (colors == null || colors.isEmpty()) {
            throw new IllegalStateException("app.task-palette.colors must have at least one entry");
        }
        Set<String> seen = new HashSet<>();
        for (PaletteColor color : colors) {
            if (color == null || color.id() == null || !ID.matcher(color.id()).matches()) {
                throw new IllegalStateException("app.task-palette: invalid id " + (color == null ? null : color.id()));
            }
            if (!seen.add(color.id())) {
                throw new IllegalStateException("app.task-palette: duplicate id " + color.id());
            }
            if (color.label() == null || color.label().isBlank()) {
                throw new IllegalStateException("app.task-palette: missing label for " + color.id());
            }
            requireShade(color.id(), "light", color.light());
            requireShade(color.id(), "dark", color.dark());
        }
        colors = List.copyOf(colors.stream()
                .map(c -> new PaletteColor(c.id(), c.label(), c.light().normalised(), c.dark().normalised()))
                .toList());
    }

    private static void requireShade(String id, String name, Shade shade) {
        if (shade == null || !isHex(shade.accent()) || !isHex(shade.bg())) {
            throw new IllegalStateException("app.task-palette: " + id + "." + name + " needs accent and bg as #RRGGBB");
        }
    }

    private static boolean isHex(String value) {
        return value != null && HEX.matcher(value).matches();
    }

    public Optional<PaletteColor> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return colors.stream().filter(c -> c.id().equals(id)).findFirst();
    }

    /** "green (Green), orange (Orange), …" for Buddy's replies. */
    public String labelsSummary() {
        return colors.stream().map(c -> c.id() + " (" + c.label() + ")").collect(Collectors.joining(", "));
    }

    public record PaletteColor(String id, String label, Shade light, Shade dark) {
    }

    public record Shade(String accent, String bg) {
        Shade normalised() {
            return new Shade(accent.toUpperCase(Locale.ROOT), bg.toUpperCase(Locale.ROOT));
        }
    }
}
