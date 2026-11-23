package com.tom.payment.routinemanager.model;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One of a user's personal goals (specs/F013-personal-goals, data-model §1). At most 3 per
 * user (enforced by PersonalGoalService). The user is referenced by id only, so the
 * {@link User} JSON is unchanged. DDL: db/F013-personal-goals.sql.
 */
@Entity
@Table(name = "personal_goal", indexes = @Index(name = "idx_personal_goal_user", columnList = "user_id, created_at"))
@Getter
@Setter
@NoArgsConstructor
public class PersonalGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "goal_text", length = 150, nullable = false)
    private String goal;

    @Column(name = "how_to_help", length = 500)
    private String howToHelp;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
