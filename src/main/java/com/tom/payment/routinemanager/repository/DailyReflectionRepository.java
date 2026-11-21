package com.tom.payment.routinemanager.repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tom.payment.routinemanager.model.DailyReflection;

public interface DailyReflectionRepository extends JpaRepository<DailyReflection, UUID> {

    Optional<DailyReflection> findByUser_IdAndReflectionDate(UUID userId, LocalDate reflectionDate);
}
