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
import jakarta.persistence.PrePersist;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class DailyTask {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String name;
    private String description;
    @JsonFormat(pattern = "HH:mm")
    private LocalTime startTime;
    private int durationMinutes;
    private boolean completed;
    /** Palette id or #RRGGBB (specs/F005). Null only on tasks created before F005. */
    @Column(length = 32)
    private String color;

    /** Task may overlap other tasks (F017). Never null in responses. */
    @Column(nullable = false, columnDefinition = "boolean not null default false")
    private Boolean overlay;

    @PrePersist
    void defaultOverlay() {
        if (overlay == null) {
            overlay = false;
        }
    }

    @JsonBackReference
    @ManyToOne
    @JoinColumn(name = "daily_routine_id")
    private DailyRoutine dailyRoutine;

    @JsonFormat(pattern = "HH:mm")
    @JsonProperty(value = "endTime", access = JsonProperty.Access.READ_ONLY)
    public LocalTime getEndTime() {
        if (startTime == null) {
            return null;
        }
        return startTime.plusMinutes(durationMinutes);
    }
}
