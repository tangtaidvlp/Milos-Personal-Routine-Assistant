package com.tom.payment.routinemanager.api;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tom.payment.routinemanager.config.TaskPaletteProperties;
import com.tom.payment.routinemanager.config.TaskPaletteProperties.PaletteColor;

import lombok.RequiredArgsConstructor;

/** The task colour palette, in round-robin order (specs/F005-…/contracts/task-palette-api.md). */
@RestController
@RequestMapping("/api/v1/task-palette")
@RequiredArgsConstructor
public class TaskPaletteController {

    private final TaskPaletteProperties palette;

    public record TaskPaletteResponse(List<PaletteColor> colors) {
    }

    @GetMapping
    public ResponseEntity<TaskPaletteResponse> getPalette() {
        return ResponseEntity.ok(new TaskPaletteResponse(palette.colors()));
    }
}
