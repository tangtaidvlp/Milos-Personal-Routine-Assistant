package com.tom.payment.routinemanager.dto;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Plan vs reality for one day (specs/F007-reflection research R3).
 * Computed on every read; never stored.
 */
public record ReflectionComparison(
        int plannedMinutes,
        int actualMinutes,
        Counts counts,
        List<Item> items) {

    public enum Status { AS_PLANNED, CHANGED, SKIPPED, UNPLANNED }

    public record Counts(int asPlanned, int changed, int skipped, int unplanned) {}

    /** {@code planned} is null for UNPLANNED, {@code actual} is null for SKIPPED. */
    public record Item(Status status, TaskRef planned, TaskRef actual) {}

    public record TaskRef(
            UUID id,
            String name,
            @JsonFormat(pattern = "HH:mm") LocalTime startTime,
            int durationMinutes) {}
}
