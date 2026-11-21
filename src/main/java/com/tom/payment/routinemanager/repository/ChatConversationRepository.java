package com.tom.payment.routinemanager.repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tom.payment.routinemanager.model.ChatConversation;
import com.tom.payment.routinemanager.model.ChatConversationType;

import jakarta.persistence.LockModeType;

public interface ChatConversationRepository extends JpaRepository<ChatConversation, UUID> {

    Optional<ChatConversation> findByUser_IdAndTypeAndDefaultRoutineId(UUID userId, ChatConversationType type, UUID defaultRoutineId);

    Optional<ChatConversation> findByUser_IdAndTypeAndRoutineDate(UUID userId, ChatConversationType type, LocalDate routineDate);

    /** Row lock used to assign message seqs and to serialise compaction. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ChatConversation c where c.id = :id")
    Optional<ChatConversation> findByIdForUpdate(@Param("id") UUID id);
}
