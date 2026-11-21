package com.tom.payment.routinemanager.model;

import java.time.Instant;
import java.time.LocalDate;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One Buddy chat thread for a user: either about a default routine
 * ({@link #defaultRoutineId}) or about one day ({@link #routineDate}).
 * Prod schema: {@code db/F004-chat-history.sql}.
 */
@Entity
@Table(name = "chat_conversation")
@Getter
@Setter
@NoArgsConstructor
public class ChatConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private ChatConversationType type;

    /** Set only for DEFAULT_ROUTINE. Plain column, not a FK, so deleting a routine never touches history. */
    @Column(name = "default_routine_id")
    private UUID defaultRoutineId;

    /** Set only for DAILY_ROUTINE and DAILY_REFLECTION. */
    @Column(name = "routine_date")
    private LocalDate routineDate;

    /** Highest message seq assigned in this conversation. */
    @Column(name = "last_message_seq", nullable = false)
    private long lastMessageSeq = 0;

    /** Buddy-only compacted context. Never returned by the API. */
    @Column(name = "summary_text", columnDefinition = "TEXT")
    private String summaryText;

    /** Messages with seq <= this are covered by {@link #summaryText}. */
    @Column(name = "summarized_through_seq", nullable = false)
    private long summarizedThroughSeq = 0;

    @Column(name = "summary_updated_at")
    private Instant summaryUpdatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public static ChatConversation forDefaultRoutine(User user, UUID defaultRoutineId) {
        ChatConversation c = new ChatConversation();
        c.user = user;
        c.type = ChatConversationType.DEFAULT_ROUTINE;
        c.defaultRoutineId = defaultRoutineId;
        return c;
    }

    public static ChatConversation forDailyRoutine(User user, LocalDate date) {
        ChatConversation c = new ChatConversation();
        c.user = user;
        c.type = ChatConversationType.DAILY_ROUTINE;
        c.routineDate = date;
        return c;
    }

    public static ChatConversation forDailyReflection(User user, LocalDate date) {
        ChatConversation c = new ChatConversation();
        c.user = user;
        c.type = ChatConversationType.DAILY_REFLECTION;
        c.routineDate = date;
        return c;
    }
}
