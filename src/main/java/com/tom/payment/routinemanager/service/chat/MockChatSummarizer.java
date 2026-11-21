package com.tom.payment.routinemanager.service.chat;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.tom.payment.routinemanager.model.ChatMessage;

/** Used when AI is off: a truncated transcript digest, so compaction can be exercised without Gemini. */
@Service
@ConditionalOnProperty(name = "spring.ai.active", havingValue = "false", matchIfMissing = true)
public class MockChatSummarizer implements ChatSummarizer {

    private static final int LINE_MAX = 80;
    private static final int TOTAL_MAX = 2000;

    @Override
    public String summarize(String previousSummary, List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        if (previousSummary != null && !previousSummary.isBlank()) {
            sb.append(previousSummary);
        }
        for (ChatMessage m : messages) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            String text = m.getContent().replaceAll("\\s+", " ");
            sb.append(m.getRole()).append(": ")
                    .append(text.length() > LINE_MAX ? text.substring(0, LINE_MAX) + "…" : text);
        }
        return sb.length() > TOTAL_MAX ? sb.substring(sb.length() - TOTAL_MAX) : sb.toString();
    }
}
