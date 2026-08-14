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
