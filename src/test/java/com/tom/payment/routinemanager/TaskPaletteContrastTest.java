package com.tom.payment.routinemanager;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.tom.payment.routinemanager.config.TaskPaletteProperties;

/**
 * Task-card text stays readable on every palette background in both themes (spec F005 FR-022).
 * Text colours mirror --color-text-primary in ui-personal-routine-manager/src/styles/tokens.css.
 */
@SpringBootTest
public class TaskPaletteContrastTest {

    private static final String LIGHT_TEXT = "#1F2937";
    private static final String DARK_TEXT = "#E6EBF2";

    @Autowired
    private TaskPaletteProperties palette;

    @Test
    void textOnEveryTaskBackgroundReachesAA() {
        for (TaskPaletteProperties.PaletteColor color : palette.colors()) {
            double light = contrast(LIGHT_TEXT, color.light().bg());
            double dark = contrast(DARK_TEXT, color.dark().bg());
            assertTrue(light >= 4.5, color.id() + " light bg contrast " + light);
            assertTrue(dark >= 4.5, color.id() + " dark bg contrast " + dark);
        }
    }

    /** WCAG 2.x contrast ratio, same maths as src/styles/contrast.ts in the UI project. */
    static double contrast(String a, String b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(String hex) {
        int rgb = Integer.parseInt(hex.substring(1), 16);
        return 0.2126 * channel((rgb >> 16) & 0xFF) + 0.7152 * channel((rgb >> 8) & 0xFF) + 0.0722 * channel(rgb & 0xFF);
    }

    private static double channel(int c) {
        double s = c / 255.0;
        return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }
}
