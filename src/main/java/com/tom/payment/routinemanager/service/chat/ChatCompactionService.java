package com.tom.payment.routinemanager.service.chat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import com.tom.payment.routinemanager.config.ChatHistoryProperties;
import com.tom.payment.routinemanager.model.ChatConversation;
import com.tom.payment.routinemanager.model.ChatMessage;
import com.tom.payment.routinemanager.repository.ChatConversationRepository;
import com.tom.payment.routinemanager.repository.ChatMessageRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Background compaction (F004 US3). After an exchange commits, if at least
 * {@code app.chat.compaction-threshold} messages are past the summary cursor,
 * they are folded into the summary and the cursor moves. Stored messages are
 * never changed. Failures change nothing and are retried after the next exchange.
 *
 * <p>The AI call runs outside any transaction/lock; the result is saved only if
 * the cursor has not moved meanwhile, so concurrent runs cannot regress it.
 */
@Slf4j
@Service
public class ChatCompactionService {

    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatSummarizer summarizer;
    private final ChatHistoryProperties properties;
    private final TransactionTemplate tx;

    public ChatCompactionService(ChatConversationRepository conversationRepository,
                                 ChatMessageRepository messageRepository,
                                 ChatSummarizer summarizer,
                                 ChatHistoryProperties properties,
                                 PlatformTransactionManager transactionManager) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.summarizer = summarizer;
        this.properties = properties;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppended(ConversationAppendedEvent event) {
        compactIfDue(event.conversationId());
    }

    /** Runs one compaction if due. Never throws. */
    public void compactIfDue(UUID conversationId) {
        try {
            Snapshot snap = tx.execute(status -> snapshot(conversationId));
            if (snap == null) {
                return;
            }
            String summary = summarizer.summarize(snap.previousSummary(), snap.messages());
            Boolean saved = tx.execute(status -> save(conversationId, snap, summary));
            if (Boolean.TRUE.equals(saved)) {
                log.info("Compacted conversation {} through seq {}", conversationId, snap.throughSeq());
            }
        } catch (RuntimeException e) {
            log.warn("Compaction failed for conversation {}; will retry after the next exchange: {}",
                    conversationId, e.toString());
        }
    }

    private Snapshot snapshot(UUID conversationId) {
        ChatConversation c = conversationRepository.findById(conversationId).orElse(null);
        if (c == null || !properties.compactionDue(c.getLastMessageSeq() - c.getSummarizedThroughSeq())) {
            return null;
        }
        List<ChatMessage> messages = messageRepository.findByConversation_IdAndSeqBetweenOrderBySeqAsc(
                conversationId, c.getSummarizedThroughSeq() + 1, c.getLastMessageSeq());
        return new Snapshot(c.getSummaryText(), c.getSummarizedThroughSeq(), c.getLastMessageSeq(), messages);
    }

    private boolean save(UUID conversationId, Snapshot snap, String summary) {
        ChatConversation c = conversationRepository.findByIdForUpdate(conversationId).orElse(null);
        if (c == null || c.getSummarizedThroughSeq() != snap.fromCursor()) {
            return false; // another run already compacted
        }
        c.setSummaryText(summary);
        c.setSummarizedThroughSeq(snap.throughSeq());
        c.setSummaryUpdatedAt(Instant.now());
        return true;
    }

    private record Snapshot(String previousSummary, long fromCursor, long throughSeq, List<ChatMessage> messages) {
    }
}
