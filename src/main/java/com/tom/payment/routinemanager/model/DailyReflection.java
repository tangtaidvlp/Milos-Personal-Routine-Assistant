package com.tom.payment.routinemanager.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * What actually happened on one day for one user (specs/F007-reflection).
 * Independent of the day's plan ({@link DailyRoutine}): copied from it once, never synced.
 * Prod schema: {@code db/F007-daily-reflection.sql}.
 */
@Entity
@Table(name = "daily_reflection",
        uniqueConstraints = @UniqueConstraint(name = "ux_daily_reflection_user_date", columnNames = {"user_id", "reflection_date"}))
@Getter
@Setter
@NoArgsConstructor
public class DailyReflection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "reflection_date", nullable = false)
    private LocalDate reflectionDate;

    @JsonManagedReference
    @OneToMany(mappedBy = "reflection", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("startTime ASC")
    private List<ReflectionTask> tasks = new ArrayList<>();

    /** F005 round-robin palette position for tasks added to the reflection. */
    @Column(name = "color_cursor", nullable = false, columnDefinition = "integer not null default 0")
    private int colorCursor = 0;

    /** Latest encouraging note from Buddy. */
    @Column(name = "feedback_text", columnDefinition = "TEXT")
    private String feedbackText;

    @Column(name = "feedback_at")
    private Instant feedbackAt;

    /** Touched on creation and on every task add/update/delete; drives "note is stale". */
    @Column(name = "content_updated_at", nullable = false)
    private Instant contentUpdatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        if (contentUpdatedAt == null) {
            contentUpdatedAt = now;
        }
    }

    public void touchContent() {
        contentUpdatedAt = Instant.now();
    }

    public boolean isFeedbackStale() {
        return feedbackAt != null && contentUpdatedAt != null && feedbackAt.isBefore(contentUpdatedAt);
    }
}
