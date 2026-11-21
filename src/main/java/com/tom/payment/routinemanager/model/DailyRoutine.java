package com.tom.payment.routinemanager.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class DailyRoutine {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private LocalDate date;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @JsonManagedReference
    @OneToMany(mappedBy = "dailyRoutine", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DailyTask> tasks = new ArrayList<>();

    /** Next round-robin palette position (specs/F005 data-model §4). */
    @JsonIgnore
    @Column(name = "color_cursor", nullable = false, columnDefinition = "integer not null default 0")
    private int colorCursor = 0;

    /** Palette id the next auto-assigned task gets. Filled by TaskColorService; never persisted. */
    @Transient
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String nextColor;
}
