package com.tom.payment.routinemanager.api;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.tom.payment.routinemanager.config.CurrentUserGuard;
import com.tom.payment.routinemanager.dto.ChatHistoryPage;
import com.tom.payment.routinemanager.dto.ChatRequest;
import com.tom.payment.routinemanager.dto.ChatResponse;
import com.tom.payment.routinemanager.dto.DailyRoutineChatRequest;
import com.tom.payment.routinemanager.dto.ReflectionChatRequest;
import com.tom.payment.routinemanager.dto.ReflectionView;
import com.tom.payment.routinemanager.funcs.DayRules;
import com.tom.payment.routinemanager.service.reflection.ReflectionAiService;
import com.tom.payment.routinemanager.service.reflection.ReflectionService;
import com.tom.payment.routinemanager.model.ChatConversation;
import com.tom.payment.routinemanager.model.DefaultRoutine;
import com.tom.payment.routinemanager.model.User;
import com.tom.payment.routinemanager.repository.DefaultRoutineRepository;
import com.tom.payment.routinemanager.service.AiChatService;
import com.tom.payment.routinemanager.service.chat.ChatContext;
import com.tom.payment.routinemanager.service.chat.ChatConversationService;

/**
 * Buddy chat. Each exchange is stored once the AI has replied, and Buddy's
 * context is rebuilt from storage (F004, specs/F004-…/contracts/chat-history-api.md).
 */
@RestController
@RequestMapping("/api/ai")
public class AIController {

    private static final Logger logger = LoggerFactory.getLogger(AIController.class);

    private final AiChatService aiChatService;
    private final ChatConversationService conversations;
    private final CurrentUserGuard userGuard;
    private final DefaultRoutineRepository defaultRoutineRepository;
    private final ReflectionService reflectionService;
    private final ReflectionAiService reflectionAi;
    private final Clock clock;

    public AIController(AiChatService aiChatService,
                        ChatConversationService conversations,
                        CurrentUserGuard userGuard,
                        DefaultRoutineRepository defaultRoutineRepository,
                        ReflectionService reflectionService,
                        ReflectionAiService reflectionAi,
                        Clock clock) {
        this.aiChatService = aiChatService;
        this.conversations = conversations;
        this.userGuard = userGuard;
        this.defaultRoutineRepository = defaultRoutineRepository;
        this.reflectionService = reflectionService;
        this.reflectionAi = reflectionAi;
        this.clock = clock;
    }

    @PostMapping("/chat/default-routine/{userId}")
    public ResponseEntity<ChatResponse> chatWithAi(
            @PathVariable UUID userId,
            @RequestBody ChatRequest request) {
        User user = userGuard.requireUser(userId);
        String message = requireMessage(request.getMessage());
        if (request.getDefaultRoutine() == null || request.getDefaultRoutine().getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "defaultRoutine.id is required");
        }
        requireOwnedRoutine(userId, request.getDefaultRoutine().getId());

        logger.info("AI chat request for user {}: {}", userId, message);
        ChatConversation conversation = conversations.getOrCreateDefaultRoutineConversation(user, request.getDefaultRoutine().getId());
        ChatContext context = conversations.buildContext(conversation.getId());
        String aiReply = aiChatService.defaultRoutineChat(userId, request.getDefaultRoutine(), context, message);
        conversations.appendExchange(conversation.getId(), message, aiReply);
        logger.info("AI chat response for user {}: {}", userId, aiReply);
        return ResponseEntity.ok(reply(aiReply));
    }

    @PostMapping("/chat/daily-routine/{userId}")
    public ResponseEntity<ChatResponse> chatWithAiForDailyRoutine(
            @PathVariable UUID userId,
            @RequestBody DailyRoutineChatRequest request) {
        User user = userGuard.requireUser(userId);
        String message = requireMessage(request.getMessage());
        if (request.getDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "date is required");
        }

        logger.info("AI daily routine chat request for user {} on {}: {}", userId, request.getDate(), message);
        ChatConversation conversation = conversations.getOrCreateDailyConversation(user, request.getDate());
        ChatContext context = conversations.buildContext(conversation.getId());
        String aiReply = aiChatService.dailyRoutineChat(userId, request.getDailyRoutine(), request.getDate(), context, message);
        conversations.appendExchange(conversation.getId(), message, aiReply);
        logger.info("AI daily routine chat response for user {}: {}", userId, aiReply);
        return ResponseEntity.ok(reply(aiReply));
    }

    @GetMapping("/chat/default-routine/{userId}/{defaultRoutineId}/messages")
    public ChatHistoryPage defaultRoutineHistory(
            @PathVariable UUID userId,
            @PathVariable UUID defaultRoutineId,
            @RequestParam(required = false) Long before,
            @RequestParam(required = false) Integer limit) {
        userGuard.requireUser(userId);
        requireOwnedRoutine(userId, defaultRoutineId);
        ChatHistoryPage page = conversations.getHistory(
                conversations.findDefaultRoutineConversation(userId, defaultRoutineId), before, limit);
        logger.info("Chat history for user {} routine {}: {} messages (before={})",
                userId, defaultRoutineId, page.messages().size(), before);
        return page;
    }

    @GetMapping("/chat/daily-routine/{userId}/{date}/messages")
    public ChatHistoryPage dailyRoutineHistory(
            @PathVariable UUID userId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long before,
            @RequestParam(required = false) Integer limit) {
        userGuard.requireUser(userId);
        ChatHistoryPage page = conversations.getHistory(
                conversations.findDailyConversation(userId, date), before, limit);
        logger.info("Chat history for user {} on {}: {} messages (before={})",
                userId, date, page.messages().size(), before);
        return page;
    }

    /**
     * Reflection chat (F007, contracts/reflection-chat-api.md): its own thread per day, and Buddy
     * only has reflection tools, so the plan is never changed from here.
     */
    @PostMapping("/chat/daily-reflection/{userId}")
    public ResponseEntity<ChatResponse> chatAboutReflection(
            @PathVariable UUID userId,
            @RequestBody ReflectionChatRequest request) {
        User user = userGuard.requireUser(userId);
        String message = requireMessage(request.message());
        LocalDate date = request.date();
        if (date == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "date is required");
        }
        if (DayRules.isFuture(date, LocalDate.now(clock))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You can reflect once the day has happened");
        }
        ReflectionView view = reflectionService.getView(user, date);
        if (!view.exists()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Reflection not found");
        }
        logger.info("AI reflection chat request for user {} on {}: {}", userId, date, message);
        ChatConversation conversation = conversations.getOrCreateReflectionConversation(user, date);
        ChatContext context = conversations.buildContext(conversation.getId());
        String aiReply = reflectionAi.chat(userId, date, view, context, message);
        conversations.appendExchange(conversation.getId(), message, aiReply);
        logger.info("AI reflection chat response for user {}: {}", userId, aiReply);
        return ResponseEntity.ok(reply(aiReply));
    }

    @GetMapping("/chat/daily-reflection/{userId}/{date}/messages")
    public ChatHistoryPage reflectionHistory(
            @PathVariable UUID userId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long before,
            @RequestParam(required = false) Integer limit) {
        userGuard.requireUser(userId);
        return conversations.getHistory(conversations.findReflectionConversation(userId, date), before, limit);
    }

    private static String requireMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message must not be blank");
        }
        return message.trim();
    }

    private void requireOwnedRoutine(UUID userId, UUID routineId) {
        DefaultRoutine routine = defaultRoutineRepository.findById(routineId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Default routine not found"));
        if (routine.getUser() == null || !userId.equals(routine.getUser().getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Default routine not found");
        }
    }

    private static ChatResponse reply(String text) {
        ChatResponse response = new ChatResponse();
        response.setReply(text);
        return response;
    }
}
