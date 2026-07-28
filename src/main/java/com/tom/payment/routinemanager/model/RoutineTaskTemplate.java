package com.tom.payment.routinemanager.model;

import java.time.LocalTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class RoutineTaskTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String name;
    @JsonFormat(pattern = "HH:mm")
    private LocalTime startTime;
    private String description;
    private int durationMinutes;
    private String color;

    @JsonBackReference
    @ManyToOne
    @JoinColumn(name = "default_routine_id")
    private DefaultRoutine defaultRoutine;

    @JsonFormat(pattern = "HH:mm")
    @JsonProperty(value = "endTime", access = JsonProperty.Access.READ_ONLY)
    public LocalTime getEndTime() {
        if (startTime == null) {
            return null;
        }
        return startTime.plusMinutes(durationMinutes);
    }
}
