package com.tom.payment.routinemanager.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.tom.payment.routinemanager.model.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    /** Buddy's context: messages after the summary cursor. */
    List<ChatMessage> findByConversation_IdAndSeqGreaterThanOrderBySeqAsc(UUID conversationId, long seq);

    /** Compaction input. */
    List<ChatMessage> findByConversation_IdAndSeqBetweenOrderBySeqAsc(UUID conversationId, long fromInclusive, long toInclusive);

    /** Newest page. */
    List<ChatMessage> findByConversation_IdOrderBySeqDesc(UUID conversationId, Pageable pageable);

    /** Older page, before a cursor. */
    List<ChatMessage> findByConversation_IdAndSeqLessThanOrderBySeqDesc(UUID conversationId, long seq, Pageable pageable);

    boolean existsByConversation_IdAndSeqLessThan(UUID conversationId, long seq);

    long countByConversation_Id(UUID conversationId);
}
