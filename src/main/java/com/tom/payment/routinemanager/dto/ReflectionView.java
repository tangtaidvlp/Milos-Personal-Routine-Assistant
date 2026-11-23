package com.tom.payment.routinemanager.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.tom.payment.routinemanager.model.ReflectionTask;

/**
 * Response of every reflection endpoint (specs/.done.F007-reflection contracts/reflection-api.md).
 * When {@code exists} is false there is no reflection yet: tasks are empty and every planned
 * task shows as SKIPPED.
 */
public record ReflectionView(
        boolean exists,
        UUID id,
        LocalDate date,
        List<ReflectionTask> tasks,
        List<ReflectionComparison.TaskRef> plannedTasks,
        ReflectionComparison comparison,
        ReflectionFeedbackView feedback,
        String nextColor) {}
