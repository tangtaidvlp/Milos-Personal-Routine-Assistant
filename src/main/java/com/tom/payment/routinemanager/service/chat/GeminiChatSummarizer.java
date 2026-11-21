package com.tom.payment.routinemanager.service.chat;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.tom.payment.routinemanager.model.ChatMessage;

/** Summarises with Gemini through its own ChatClient: no tools, no advisors. */
@Service
@ConditionalOnProperty(name = "spring.ai.active", havingValue = "true")
public class GeminiChatSummarizer implements ChatSummarizer {

    private static final String SYSTEM = """
            Summarise this conversation between a user and their routine assistant.
            Keep user preferences, decisions, constraints and any changes made to routines/tasks.
            If a previous summary is given, merge it with the new messages into one summary.
            Max 200 words. Plain text.
            """;

    private final ChatClient chatClient;

    public GeminiChatSummarizer(ChatClient.Builder builder) {
        // Builder is a prototype bean, so this client does not share the main chat client's tools.
        this.chatClient = builder.defaultSystem(SYSTEM).build();
    }

    @Override
    public String summarize(String previousSummary, List<ChatMessage> messages) {
        StringBuilder input = new StringBuilder();
        if (previousSummary != null && !previousSummary.isBlank()) {
            input.append("Previous summary:\n").append(previousSummary).append("\n\n");
        }
        input.append("New messages:\n");
        for (ChatMessage m : messages) {
            input.append(m.getRole() == com.tom.payment.routinemanager.model.ChatRole.USER ? "USER: " : "ASSISTANT: ")
                    .append(m.getContent()).append('\n');
        }
        String summary = chatClient.prompt().user(input.toString()).call().content();
        if (summary == null || summary.isBlank()) {
            throw new IllegalStateException("Empty summary from AI");
        }
        return summary.trim();
    }
}
