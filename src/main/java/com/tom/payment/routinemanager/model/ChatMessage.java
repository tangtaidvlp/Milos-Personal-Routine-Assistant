package com.tom.payment.routinemanager.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One stored message in a {@link ChatConversation}. Immutable once written. */
@Entity
@Table(name = "chat_message",
        uniqueConstraints = @UniqueConstraint(name = "ux_chat_message_seq", columnNames = {"conversation_id", "seq"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private ChatConversation conversation;

    /** Unique per conversation, strictly increasing. */
    @Column(name = "seq", nullable = false)
    private long seq;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private ChatRole role;

    /** Full text, never truncated. */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public ChatMessage(ChatConversation conversation, long seq, ChatRole role, String content, Instant createdAt) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Chat message content must not be blank");
        }
        this.conversation = conversation;
        this.seq = seq;
        this.role = role;
        this.content = content;
        this.createdAt = createdAt;
    }
}
