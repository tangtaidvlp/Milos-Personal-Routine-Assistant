package com.tom.payment.routinemanager.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tom.payment.routinemanager.model.PersonalGoal;

/** Every lookup is scoped by the owner (constitution IV). */
public interface PersonalGoalRepository extends JpaRepository<PersonalGoal, UUID> {

    List<PersonalGoal> findByUserIdOrderByCreatedAtAscIdAsc(UUID userId);

    Optional<PersonalGoal> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);
}
