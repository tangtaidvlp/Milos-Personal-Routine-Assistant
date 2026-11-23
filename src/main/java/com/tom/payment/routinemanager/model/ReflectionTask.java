package com.tom.payment.routinemanager.model;

import java.time.LocalTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One thing the user actually did (specs/.done.F007-reflection data-model). */
@Entity
@Table(name = "reflection_task")
@Getter
@Setter
@NoArgsConstructor
public class ReflectionTask {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The daily_task this was copied from. Plain column, no FK; null = unplanned. */
    @Column(name = "planned_task_id")
    private UUID plannedTaskId;

    @Column(nullable = false)
    private String name;

    private String description;

    @JsonFormat(pattern = "HH:mm")
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    /** Palette id or #RRGGBB (specs/F005). */
    @Column(length = 32)
    private String color;

    @JsonBackReference
    @ManyToOne(optional = false)
    @JoinColumn(name = "daily_reflection_id", nullable = false)
    private DailyReflection reflection;

    @JsonFormat(pattern = "HH:mm")
    @JsonProperty(value = "endTime", access = JsonProperty.Access.READ_ONLY)
    public LocalTime getEndTime() {
        return startTime == null ? null : startTime.plusMinutes(durationMinutes);
    }
}
