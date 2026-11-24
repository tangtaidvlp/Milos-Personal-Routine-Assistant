package com.tom.payment.routinemanager.service.chat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import jakarta.persistence.EntityManager;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.tom.payment.routinemanager.config.ChatHistoryProperties;
import com.tom.payment.routinemanager.dto.ChatHistoryPage;
import com.tom.payment.routinemanager.dto.ChatMessageDto;
import com.tom.payment.routinemanager.model.ChatConversation;
import com.tom.payment.routinemanager.model.ChatConversationType;
import com.tom.payment.routinemanager.model.ChatMessage;
import com.tom.payment.routinemanager.model.ChatRole;
import com.tom.payment.routinemanager.model.User;
import com.tom.payment.routinemanager.repository.ChatConversationRepository;
import com.tom.payment.routinemanager.repository.ChatMessageRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Stores Buddy conversations and builds Buddy's context from them.
 * One conversation per (user, default routine), per (user, date), per (user, date) reflection (F007)
 * and one Personal Target chat per user (F013 US4).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatConversationService {

    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatHistoryProperties properties;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transactionTemplate;
    private final EntityManager entityManager;

    // ---------- lookup ----------

    @Transactional(readOnly = true)
    public Optional<ChatConversation> findDefaultRoutineConversation(UUID userId, UUID defaultRoutineId) {
        return conversationRepository.findByUser_IdAndTypeAndDefaultRoutineId(
                userId, ChatConversationType.DEFAULT_ROUTINE, defaultRoutineId);
    }

    @Transactional(readOnly = true)
    public Optional<ChatConversation> findDailyConversation(UUID userId, LocalDate date) {
        return conversationRepository.findByUser_IdAndTypeAndRoutineDate(
                userId, ChatConversationType.DAILY_ROUTINE, date);
    }

    /** The reflection chat for a day (F007): separate from that day's planning chat. */
    @Transactional(readOnly = true)
    public Optional<ChatConversation> findReflectionConversation(UUID userId, LocalDate date) {
        return conversationRepository.findByUser_IdAndTypeAndRoutineDate(
                userId, ChatConversationType.DAILY_REFLECTION, date);
    }

    /** The Personal Target chat (F013 US4): one per user. */
    @Transactional(readOnly = true)
    public Optional<ChatConversation> findPersonalTargetConversation(UUID userId) {
        return conversationRepository.findByUser_IdAndType(userId, ChatConversationType.PERSONAL_TARGET);
    }

    public ChatConversation getOrCreatePersonalTargetConversation(User user) {
        return getOrCreate(
                () -> findPersonalTargetConversation(user.getId()),
                () -> ChatConversation.forPersonalTarget(user));
    }

    public ChatConversation getOrCreateReflectionConversation(User user, LocalDate date) {
        return getOrCreate(
                () -> findReflectionConversation(user.getId(), date),
                () -> ChatConversation.forDailyReflection(user, date));
    }

    public ChatConversation getOrCreateDefaultRoutineConversation(User user, UUID defaultRoutineId) {
        return getOrCreate(
                () -> findDefaultRoutineConversation(user.getId(), defaultRoutineId),
                () -> ChatConversation.forDefaultRoutine(user, defaultRoutineId));
    }

    public ChatConversation getOrCreateDailyConversation(User user, LocalDate date) {
        return getOrCreate(
                () -> findDailyConversation(user.getId(), date),
                () -> ChatConversation.forDailyRoutine(user, date));
    }

    private ChatConversation getOrCreate(Supplier<Optional<ChatConversation>> find, Supplier<ChatConversation> create) {
        Optional<ChatConversation> existing = find.get();
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            return transactionTemplate.execute(status -> conversationRepository.saveAndFlush(create.get()));
        } catch (DataIntegrityViolationException race) {
            // Another request created it first (unique index on the subject).
            return find.get().orElseThrow(() -> race);
        }
    }

    // ---------- Buddy context ----------

    /** Summary + every message after it, oldest first. */
    @Transactional(readOnly = true)
    public ChatContext buildContext(UUID conversationId) {
        ChatConversation c = conversationRepository.findById(conversationId).orElseThrow();
        List<Message> recent = new ArrayList<>();
        for (ChatMessage m : messageRepository.findByConversation_IdAndSeqGreaterThanOrderBySeqAsc(
                conversationId, c.getSummarizedThroughSeq())) {
            recent.add(m.getRole() == ChatRole.USER
                    ? new UserMessage(m.getContent())
                    : new AssistantMessage(m.getContent()));
        }
        return new ChatContext(c.getSummaryText(), recent);
    }

    // ---------- writes ----------

    /**
     * Stores one user message and Buddy's reply. Called only after the AI
     * replied, so a failed call stores nothing.
     */
    @Transactional
    public void appendExchange(UUID conversationId, String userText, String assistantText) {
        ChatConversation c = conversationRepository.findByIdForUpdate(conversationId).orElseThrow();
        // With open-in-view the conversation is usually already in this request's persistence
        // context, and the locking query hands back that cached copy. Re-read it now that we
        // hold the row lock, so concurrent messages never reuse a seq (F008, found by
        // ChatSequenceLockingIntegrationTest).
        entityManager.refresh(c);
        long seq = c.getLastMessageSeq();
        Instant now = Instant.now();
        messageRepository.save(new ChatMessage(c, seq + 1, ChatRole.USER, userText, now));
        messageRepository.save(new ChatMessage(c, seq + 2, ChatRole.ASSISTANT, assistantText, now));
        c.setLastMessageSeq(seq + 2);
        events.publishEvent(new ConversationAppendedEvent(conversationId));
    }

    // ---------- history ----------

    /**
     * Newest {@code limit} messages (or those before {@code before}),
     * returned oldest first.
     */
    @Transactional(readOnly = true)
    public ChatHistoryPage getHistory(Optional<ChatConversation> conversation, Long before, Integer limit) {
        if (conversation.isEmpty()) {
            return ChatHistoryPage.empty();
        }
        UUID id = conversation.get().getId();
        int size = properties.pageSizeFor(limit);
        PageRequest page = PageRequest.of(0, size);

        List<ChatMessage> desc = before == null
                ? messageRepository.findByConversation_IdOrderBySeqDesc(id, page)
                : messageRepository.findByConversation_IdAndSeqLessThanOrderBySeqDesc(id, before, page);
        if (desc.isEmpty()) {
            return ChatHistoryPage.empty();
        }
        List<ChatMessage> asc = new ArrayList<>(desc);
        Collections.reverse(asc);

        long firstSeq = asc.get(0).getSeq();
        boolean hasMore = messageRepository.existsByConversation_IdAndSeqLessThan(id, firstSeq);
        return new ChatHistoryPage(
                asc.stream().map(ChatMessageDto::from).toList(),
                hasMore,
                hasMore ? firstSeq : null);
    }
}
