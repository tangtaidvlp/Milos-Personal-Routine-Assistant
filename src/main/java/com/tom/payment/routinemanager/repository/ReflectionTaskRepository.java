package com.tom.payment.routinemanager.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tom.payment.routinemanager.model.ReflectionTask;

public interface ReflectionTaskRepository extends JpaRepository<ReflectionTask, UUID> {
}
