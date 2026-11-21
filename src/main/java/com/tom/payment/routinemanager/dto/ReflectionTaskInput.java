package com.tom.payment.routinemanager.dto;

import java.time.LocalTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;

/** Create (id ignored) or update (id required) a reflection task. A null color keeps / auto-assigns. */
public record ReflectionTaskInput(
        UUID id,
        String name,
        String description,
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        Integer durationMinutes,
        String color) {}
