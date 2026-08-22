package com.tom.payment.routinemanager.service;

import java.util.UUID;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tom.payment.routinemanager.funcs.JsonFunc;
import com.tom.payment.routinemanager.model.DailyRoutine;
import com.tom.payment.routinemanager.model.DefaultRoutine;
import com.tom.payment.routinemanager.service.aitools.DailyRoutineAiTools;
import com.tom.payment.routinemanager.service.aitools.DefaultRoutineAiTools;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Wires the Gemini ChatClient with our @Tool-annotated tool beans and
 * handles the chat interaction for each user.
 */
@Slf4j
@Service
@Primary
@ConditionalOnProperty(name = "spring.ai.active", havingValue = "true")
public class GeminiAiChatService implements AiChatService {

    private final ChatClient chatClient;
    private final JsonFunc jsonFunc;

    public GeminiAiChatService(
            ChatClient.Builder chatClientBuilder,
            @Qualifier("defaultRoutineChatMemory") ChatMemory chatMemory,
            JsonFunc jsonFunc,
            DefaultRoutineAiTools defaultRoutineAiTools,
            DailyRoutineAiTools dailyRoutineAiTools) {
        log.info("Initializing GeminiAiChatService with Gemini ChatClient and AI tools.");
        this.chatClient = chatClientBuilder
                .defaultSystem("""
                        You are a helpful Personal Routine Assistant.
                        Help the user manage their daily and default routines.
                        When the user asks to add, update, or delete tasks, use the available tools.
                        Always confirm what you did after making changes.
                        NOTE: DO NOT ANSWER THINGS OUTSIDE OF OUR SCOPE.
                        If the user asks about things outside of daily routines, default routines, or tasks, politely inform them that you can only assist with those topics.
                        """)
                .defaultTools(defaultRoutineAiTools, dailyRoutineAiTools)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
        this.jsonFunc = jsonFunc;
    }

    @Override
    public String chat(UUID userId, String userMessage) {
        return chatClient.prompt()
                .system("The current user's ID is: " + userId
                        + ". You MUST use this ID whenever a tool requires a 'userId' parameter. "
                        + "Today's date is: " + java.time.LocalDate.now() + ".")
                .user(userMessage)
                .advisors(a -> a.param(org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID, userId.toString()))
                .call()
                .content();
    }

    @Override
    public String defaultRoutineChat(UUID userId, DefaultRoutine defaultRoutine, String userMessage) {
        return chatClient.prompt()
                .system("Current user's ID is: " + userId
                        + ". You MUST use this ID whenever a tool requires a 'userId' parameter. "
                        + "User is asking to interact on this default routine: " + jsonFunc.parseToJson(defaultRoutine) + ". "
                        + "ID is ID. "
                        + "tasks are the routine tasks item, you need to identify which tasks user are mention base on their prompt and perform the request. "
                        + "Today's date is: " + java.time.LocalDate.now() + ".")
                .user(userMessage)
                .advisors(a -> a.param(org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID, userId.toString().concat("-"+defaultRoutine.getId().toString())))
                .call()
                .content();
    }

    @Override
    public String dailyRoutineChat(UUID userId, DailyRoutine dailyRoutine, java.time.LocalDate date, String userMessage) {
        boolean isPastDate = date.isBefore(java.time.LocalDate.now());
        return chatClient.prompt()
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
                        + "Today's date is: " + java.time.LocalDate.now() + ".")
                .user(userMessage)
                .advisors(a -> a.param(org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID, userId.toString().concat("-daily-" + date.toString())))
                .call()
                .content();
    }
}

@Slf4j
@Configuration
class ChatMemoryConfig {

    @Bean
    @Qualifier("defaultRoutineChatMemory")
    public ChatMemory chatMemory() {
        // Keeps the last N messages per conversation in memory (default in-memory repository)
        log.debug("Initializing ChatMemory with a maximum of 20 messages per conversation.");
        return MessageWindowChatMemory.builder()
                .maxMessages(20)
                .build();
    }
}