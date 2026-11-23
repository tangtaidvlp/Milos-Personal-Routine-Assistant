package com.tom.payment.routinemanager.service;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.tom.payment.routinemanager.config.TaskPaletteProperties;
import com.tom.payment.routinemanager.model.DailyRoutine;
import com.tom.payment.routinemanager.model.DefaultRoutine;

import lombok.RequiredArgsConstructor;

/**
 * Task colours (specs/.done.F005-i-want-color-to-be-persisted): validation and round-robin assignment.
 * A colour is a palette id (follows the theme) or an exact {@code #RRGGBB} (user-chosen).
 */
@Service
@RequiredArgsConstructor
public class TaskColorService {

    /**
     * Palette ids for Buddy's tool descriptions, which must be compile-time constants.
     * Keep in sync with {@code app.task-palette.colors} in application.yml; Buddy's replies list the live palette.
     */
    public static final String PALETTE_IDS_HINT = "green, orange, blue, grey, slate, peach, pink, purple";

    private static final Pattern HEX = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    private final TaskPaletteProperties palette;

    public boolean isValid(String color) {
        return color != null && (palette.byId(color).isPresent() || HEX.matcher(color).matches());
    }

    public void requireValid(String color) {
        if (!isValid(color)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown task color '" + color + "'");
        }
    }

    /** Palette id at a cursor position, wrapping round. */
    public String nextColor(int cursor) {
        List<TaskPaletteProperties.PaletteColor> colors = palette.colors();
        return colors.get(Math.floorMod(cursor, colors.size())).id();
    }

    /**
     * Gives colours to tasks about to be created in one routine, in request order (data-model §4):
     * no colour → the next palette id, or the colour of an earlier same-named task in this request;
     * the next palette id given explicitly (dialog pre-selection kept) → moves the cursor;
     * any other valid colour → kept, cursor unchanged.
     *
     * @return the routine's new cursor
     */
    public <T> int assign(int cursor, List<T> newTasks, Function<T, String> getName,
                          Function<T, String> getColor, BiConsumer<T, String> setColor) {
        if (newTasks == null) {
            return cursor;
        }
        Map<String, String> colorByName = new HashMap<>();
        for (T task : newTasks) {
            String next = nextColor(cursor);
            String name = normalisedName(getName.apply(task));
            String color = getColor.apply(task);
            if (color == null) {
                String sameName = name == null ? null : colorByName.get(name);
                if (sameName != null) {
                    color = sameName;
                } else {
                    color = next;
                    cursor++;
                }
                setColor.accept(task, color);
            } else {
                requireValid(color);
                if (color.equals(next)) {
                    cursor++;
                }
            }
            if (name != null) {
                colorByName.putIfAbsent(name, color);
            }
        }
        return cursor;
    }

    /** Palette id matched case-insensitively, for Buddy. Exact colours are not accepted here. */
    public Optional<String> paletteIdFor(String requested) {
        if (requested == null) {
            return Optional.empty();
        }
        String id = requested.trim().toLowerCase(Locale.ROOT);
        return palette.byId(id).map(TaskPaletteProperties.PaletteColor::id);
    }

    /** "Unknown color 'x'. Available colors: green (Green), …" for Buddy's replies. */
    public String unknownColorMessage(String requested) {
        return "Unknown color '" + requested + "'. Available colors: " + palette.labelsSummary() + ".";
    }

    public DefaultRoutine fillNextColor(DefaultRoutine routine) {
        if (routine != null) {
            routine.setNextColor(nextColor(routine.getColorCursor()));
        }
        return routine;
    }

    public DailyRoutine fillNextColor(DailyRoutine routine) {
        if (routine != null) {
            routine.setNextColor(nextColor(routine.getColorCursor()));
        }
        return routine;
    }

    private static String normalisedName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
