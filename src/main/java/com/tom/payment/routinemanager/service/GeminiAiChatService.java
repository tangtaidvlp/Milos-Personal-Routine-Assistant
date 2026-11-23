package com.tom.payment.routinemanager.service;

import java.time.Clock;
import java.time.LocalTime;
import java.util.UUID;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.tom.payment.routinemanager.funcs.DayRules;
import com.tom.payment.routinemanager.funcs.JsonFunc;
import com.tom.payment.routinemanager.model.DailyRoutine;
import com.tom.payment.routinemanager.model.DefaultRoutine;
import com.tom.payment.routinemanager.service.aitools.DailyRoutineAiTools;
import com.tom.payment.routinemanager.service.aitools.DefaultRoutineAiTools;
import com.tom.payment.routinemanager.service.aitools.GoalProposalTool;
import com.tom.payment.routinemanager.service.chat.ChatContext;

import lombok.extern.slf4j.Slf4j;

/**
 * Wires the Gemini ChatClient with our @Tool-annotated tool beans and
 * handles the chat interaction for each user.
 *
 * <p>Stateless: there is no chat-memory advisor. Earlier conversation comes in
 * as a {@link ChatContext} built from the database by ChatConversationService,
 * so Buddy keeps context across restarts and a failed call stores nothing.
 */
@Slf4j
@Service
@Primary
@ConditionalOnProperty(name = "spring.ai.active", havingValue = "true")
public class GeminiAiChatService implements AiChatService {

    private final ChatClient chatClient;
    private final JsonFunc jsonFunc;
    private final Clock clock;
    private final PersonalGoalService goalService;
    private final BuddyContextService buddyContext;

    public GeminiAiChatService(
            ChatClient.Builder chatClientBuilder,
            JsonFunc jsonFunc,
            DefaultRoutineAiTools defaultRoutineAiTools,
            DailyRoutineAiTools dailyRoutineAiTools,
            Clock clock,
            PersonalGoalService goalService,
            BuddyContextService buddyContext) {
        log.info("Initializing GeminiAiChatService with Gemini ChatClient and AI tools.");
        this.chatClient = chatClientBuilder
                .defaultSystem("""
                        You are a helpful Personal Routine Assistant.
                        Help the user manage their daily and default routines, guided by their personal goals.
                        When the user asks to add, update, or delete tasks, use the available tools.
                        Always confirm what you did after making changes.
                        NOTE: DO NOT ANSWER THINGS OUTSIDE OF OUR SCOPE.
                        If the user asks about things outside of daily routines, default routines, tasks or their personal goals, politely inform them that you can only assist with those topics.
                        """)
                .defaultTools(defaultRoutineAiTools, dailyRoutineAiTools)
                .build();
        this.jsonFunc = jsonFunc;
        this.clock = clock;
        this.goalService = goalService;
        this.buddyContext = buddyContext;
    }

    @Override
    public BuddyReply defaultRoutineChat(UUID userId, DefaultRoutine defaultRoutine, ChatContext context, String userMessage, LocalTime localTime) {
        // F013: the user's goals, and a per-call tool that can only propose goal changes (research R4, R5).
        GoalProposalTool proposals = new GoalProposalTool(userId, goalService);
        String content = chatClient.prompt()
                .system("Current user's ID is: " + userId
                        + ". You MUST use this ID whenever a tool requires a 'userId' parameter. "
                        + "User is asking to interact on this default routine: " + jsonFunc.parseToJson(defaultRoutine) + ". "
                        + "ID is ID. "
                        + "tasks are the routine tasks item, you need to identify which tasks user are mention base on their prompt and perform the request. "
                        + "Today's date is: " + java.time.LocalDate.now(clock) + "."
                        + buddyContext.promptBlock(userId, BuddyCall.DEFAULT_ROUTINE_CHAT, localTime)
                        + summaryBlock(context))
                .messages(context.recentMessages())
                .user(userMessage)
                .tools(proposals)
                .call()
                .content();
        return new BuddyReply(content, proposals.proposal().orElse(null));
    }

    @Override
    public BuddyReply dailyRoutineChat(UUID userId, DailyRoutine dailyRoutine, java.time.LocalDate date, ChatContext context, String userMessage, LocalTime localTime) {
        boolean isPastDate = DayRules.isPast(date, java.time.LocalDate.now(clock));
        GoalProposalTool proposals = new GoalProposalTool(userId, goalService);
        String content = chatClient.prompt()
                .system("Current user's ID is: " + userId
                        + ". You MUST use this ID whenever a tool requires a 'userId' parameter. "
                        + "User is asking to interact on their daily routine for date: " + date + ". "
                        + "Current daily routine state: " + jsonFunc.parseToJson(dailyRoutine) + ". "
                        + "ID is ID. "
                        + "tasks are the routine tasks item, you need to identify which tasks user are mention base on their prompt and perform the request. "
                        + (isPastDate
                                ? "This date is in the past. Do NOT add new tasks or change any task's time schedule (startTime/durationMinutes) for this date. "
                                        + "Only completed status, name, or description may be changed. Politely explain this limitation if the user asks to reschedule. "
                                : "")
                        + "Today's date is: " + java.time.LocalDate.now(clock) + "."
                        + buddyContext.promptBlock(userId, BuddyCall.DAILY_CHAT, localTime)
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
