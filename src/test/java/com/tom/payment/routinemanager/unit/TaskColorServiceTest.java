package com.tom.payment.routinemanager.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.tom.payment.routinemanager.config.TaskPaletteProperties;
import com.tom.payment.routinemanager.config.TaskPaletteProperties.PaletteColor;
import com.tom.payment.routinemanager.config.TaskPaletteProperties.Shade;
import com.tom.payment.routinemanager.service.TaskColorService;

/** Rule: which colour a new task gets (F005, round-robin over the palette). */
class TaskColorServiceTest {

    private static final List<String> IDS = List.of("green", "orange", "blue", "grey", "slate", "peach", "pink", "purple");

    private final TaskColorService colors = new TaskColorService(palette());

    /** A task as the service sees it: a name and a colour. */
    private static final class Task {
        final String name;
        String color;

        Task(String name, String color) {
            this.name = name;
            this.color = color;
        }
    }

    private int assign(int cursor, List<Task> tasks) {
        return colors.assign(cursor, tasks, t -> t.name, t -> t.color, (t, c) -> t.color = c);
    }

    @Test
    void colourlessTasksGoRoundTheWholePaletteAndWrap() {
        int cursor = 0;
        List<String> given = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Task task = new Task("Task " + i, null);
            cursor = assign(cursor, List.of(task));
            given.add(task.color);
        }
        List<String> expected = new ArrayList<>(IDS);
        expected.addAll(List.of("green", "orange"));
        assertEquals(expected, given);
        assertEquals("blue", colors.nextColor(cursor));
    }

    @Test
    void sendingTheNextPaletteColourKeepsItAndAdvances() {
        Task task = new Task("A", "green");
        assertEquals(1, assign(0, List.of(task)));
        assertEquals("green", task.color);
    }

    @Test
    void anotherPaletteColourIsKeptWithoutAdvancing() {
        Task task = new Task("B", "pink");
        assertEquals(1, assign(1, List.of(task)));
        assertEquals("pink", task.color);
    }

    @Test
    void anExactColourIsKeptWithoutAdvancing() {
        Task task = new Task("C", "#FF0000");
        assertEquals(1, assign(1, List.of(task)));
        assertEquals("#FF0000", task.color);
    }

    @Test
    void sameNameInOneRequestSharesAColour() {
        Task sleep = new Task("Sleep", null);
        Task sleepAgain = new Task(" sleep ", null);
        Task read = new Task("Read", null);
        int cursor = assign(0, List.of(sleep, sleepAgain, read));
        assertEquals("green", sleep.color);
        assertEquals("green", sleepAgain.color);
        assertEquals("orange", read.color);
        assertEquals("blue", colors.nextColor(cursor));
    }

    @Test
    void unknownColourIsRefused() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> colors.requireValid("gold"));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        assertThrows(ResponseStatusException.class, () -> assign(0, List.of(new Task("Bad", "gold"))));
    }

    @Test
    void buddyMayUsePaletteNamesInAnyCaseButNotExactColours() {
        assertEquals(Optional.of("purple"), colors.paletteIdFor("Purple"));
        assertEquals(Optional.of("purple"), colors.paletteIdFor(" PURPLE "));
        assertEquals(Optional.empty(), colors.paletteIdFor("#ff0000"));
        assertEquals(Optional.empty(), colors.paletteIdFor("gold"));
        assertEquals(Optional.empty(), colors.paletteIdFor(null));
    }

    @Test
    void unknownColourMessageListsThePalette() {
        String message = colors.unknownColorMessage("gold");
        assertTrue(message.contains("Available colors:"), message);
        for (String label : List.of("Green", "Orange", "Blue", "Grey", "Slate", "Peach", "Pink", "Purple")) {
            assertTrue(message.contains(label), message);
        }
    }

    private static TaskPaletteProperties palette() {
        List<PaletteColor> list = new ArrayList<>();
        for (String id : IDS) {
            String label = Character.toUpperCase(id.charAt(0)) + id.substring(1);
            list.add(new PaletteColor(id, label, new Shade("#111111", "#EEEEEE"), new Shade("#EEEEEE", "#111111")));
        }
        return new TaskPaletteProperties(list);
    }
}
