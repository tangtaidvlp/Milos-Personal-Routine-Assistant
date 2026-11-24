package com.tom.payment.routinemanager.service;

import java.time.LocalTime;
import java.util.UUID;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.tom.payment.routinemanager.service.aitools.GoalProposalTool;
import com.tom.payment.routinemanager.service.chat.ChatContext;

/**
 * Gemini-backed Buddy for the Personal Target page (specs/F013-personal-goals research R11).
 * The client comes from its own prototype builder with no default tools, so the plan tools
 * registered by {@link GeminiAiChatService} never reach this chat; each call adds only
 * {@link GoalProposalTool}, which never writes.
 */
@Service
@ConditionalOnProperty(name = "spring.ai.active", havingValue = "true")
public class GeminiPersonalTargetAiService implements PersonalTargetAiService {

    private static final String SYSTEM = """
            You are Routine Buddy on the user's Personal Target page, where they keep up to 3 personal goals.
            Help the user shape clear, realistic goals and say how you should help with each one.
            When a goal is ready, propose it with the proposeGoal tool; the user confirms or dismisses it on screen.
            You cannot change their plan, tasks or routines here. If they ask, tell them to use the Buddy chat on
            the Today or Default routine page.
            Only talk about the user's goals, routines and habits; politely decline anything else.
            """;

    private final ChatClient chatClient;
    private final PersonalGoalService goalService;
    private final BuddyContextService buddyContext;

    public GeminiPersonalTargetAiService(ChatClient.Builder chatBuilder,
                                         PersonalGoalService goalService,
                                         BuddyContextService buddyContext) {
        // ChatClient.Builder is a prototype bean: this builder shares no tools with the plan chat.
        this.chatClient = chatBuilder.defaultSystem(SYSTEM).build();
        this.goalService = goalService;
        this.buddyContext = buddyContext;
    }

    @Override
    public BuddyReply chat(UUID userId, ChatContext context, String userMessage, LocalTime localTime) {
        GoalProposalTool proposals = new GoalProposalTool(userId, goalService);
        String content = chatClient.prompt()
                .system(SYSTEM
                        + buddyContext.promptBlock(userId, BuddyCall.PERSONAL_TARGET_CHAT, localTime)
                        + summaryBlock(context))
                .messages(context.recentMessages())
                .user(userMessage)
                .tools(proposals)
                .call()
                .content();
        return new BuddyReply(content, proposals.proposal().orElse(null));
    }

    private static String summaryBlock(ChatContext context) {
        if (context.summary() == null || context.summary().isBlank()) {
            return "";
        }
        return "\n\nSummary of the earlier conversation with this user (older messages are not repeated below):\n"
                + context.summary();
    }
}
