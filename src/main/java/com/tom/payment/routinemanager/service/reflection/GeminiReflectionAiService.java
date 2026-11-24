package com.tom.payment.routinemanager.service.reflection;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.tom.payment.routinemanager.dto.ReflectionView;
import com.tom.payment.routinemanager.funcs.JsonFunc;
import com.tom.payment.routinemanager.service.BuddyCall;
import com.tom.payment.routinemanager.service.BuddyContextService;
import com.tom.payment.routinemanager.service.BuddyReply;
import com.tom.payment.routinemanager.service.PersonalGoalService;
import com.tom.payment.routinemanager.service.aitools.GoalProposalTool;
import com.tom.payment.routinemanager.service.aitools.ReflectionAiTools;
import com.tom.payment.routinemanager.service.chat.ChatContext;

import lombok.extern.slf4j.Slf4j;

/**
 * Gemini-backed Buddy for reflections (specs/.done.F007-reflection research R4/R5).
 * Two clients from prototype builders: chat has ONLY the reflection tools, so it can
 * never change the plan; feedback has no tools at all.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "spring.ai.active", havingValue = "true")
public class GeminiReflectionAiService implements ReflectionAiService {

    static final String TONE_RULES = """
            Tone rules (always follow them):
            - Be warm, kind and encouraging. The user must feel it is okay that the day differed from the plan.
            - Never use harsh, judgmental or guilt-inducing words (e.g. "failed", "should have", "lazy", "only", "behind", "wasted").
            - Never compare the user to other people.
            """;

    private static final String CHAT_SYSTEM = """
            You are Routine Buddy, helping the user record what REALLY happened in their day (their reflection).
            The reflection is separate from their plan. You can only change the reflection, using the reflection tools.
            When the user describes their day, update the reflection to match: change start times or durations,
            remove things they skipped, add things they did that were not planned.
            Use task ids from the reflection state. After changing anything, tell the user briefly what you added, changed or removed.
            If it is unclear which task the user means, ask one short follow-up question instead of guessing.
            Only talk about the user's day, routine, reflection and personal goals; politely decline anything else.
            """ + TONE_RULES;

    private static final String FEEDBACK_SYSTEM = """
            You are Routine Buddy. Write a short note (60-120 words, plain text, no headings or lists) about the user's day,
            based on the plan-vs-reality comparison you are given.
            - Start with something genuinely positive the user did.
            - Then give AT MOST two gentle, concrete suggestions for tomorrow, phrased as invitations ("you could try ...").
            - End with a short reassuring line, e.g. that tomorrow is a fresh start.
            - Even if almost nothing went to plan, stay reassuring.
            """ + TONE_RULES;

    private final ChatClient chatClient;
    private final ChatClient feedbackClient;
    private final JsonFunc jsonFunc;
    private final Clock clock;
    private final PersonalGoalService goalService;
    private final BuddyContextService buddyContext;

    public GeminiReflectionAiService(ChatClient.Builder chatBuilder,
                                     ChatClient.Builder feedbackBuilder,
                                     ReflectionAiTools reflectionAiTools,
                                     JsonFunc jsonFunc,
                                     Clock clock,
                                     PersonalGoalService goalService,
                                     BuddyContextService buddyContext) {
        // ChatClient.Builder is a prototype bean: each parameter is a fresh builder, so these
        // clients share no tools with GeminiAiChatService (which holds the plan tools).
        this.chatClient = chatBuilder.defaultSystem(CHAT_SYSTEM).defaultTools(reflectionAiTools).build();
        this.feedbackClient = feedbackBuilder.defaultSystem(FEEDBACK_SYSTEM).build();
        this.jsonFunc = jsonFunc;
        this.clock = clock;
        this.goalService = goalService;
        this.buddyContext = buddyContext;
    }

    @Override
    public String feedback(UUID userId, ReflectionView view, LocalTime localTime) {
        // E001: the user's goals (F013) and tone (F014); this client has no tools, so no proposals.
        return feedbackClient.prompt()
                .system(FEEDBACK_SYSTEM + buddyContext.promptBlock(userId, BuddyCall.REFLECTION_FEEDBACK, localTime)
                        + "\nIf the user has goals, connect the note to one of them where it fits naturally."
                        + " Keep following the tone rules above in the user's chosen tone.")
                .user("Date: " + view.date() + "\nComparison: " + jsonFunc.parseToJson(view.comparison()))
                .call()
                .content();
    }

    @Override
    public BuddyReply chat(UUID userId, LocalDate date, ReflectionView view, ChatContext context, String userMessage, LocalTime localTime) {
        GoalProposalTool proposals = new GoalProposalTool(userId, goalService);
        String content = chatClient.prompt()
                .system(CHAT_SYSTEM
                        + "\nCurrent user's ID is: " + userId + ". You MUST use this ID whenever a tool requires a 'userId'."
                        + "\nThe reflection is for date: " + date + " (use this exact date in tool calls)."
                        + "\nCurrent reflection state (tasks = what really happened; plannedTasks = the plan, read only): "
                        + jsonFunc.parseToJson(view)
                        + "\nToday's date is: " + LocalDate.now(clock) + "."
                        + buddyContext.promptBlock(userId, BuddyCall.REFLECTION_CHAT, localTime)
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
