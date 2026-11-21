package com.tom.payment.routinemanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tom.payment.routinemanager.model.ChatConversationType;
import com.tom.payment.routinemanager.model.DefaultRoutine;
import com.tom.payment.routinemanager.model.User;
import com.tom.payment.routinemanager.repository.ChatConversationRepository;
import com.tom.payment.routinemanager.repository.DefaultRoutineRepository;
import com.tom.payment.routinemanager.service.AiChatService;
import com.tom.payment.routinemanager.service.UserService;
import com.tom.payment.routinemanager.service.chat.ChatContext;

/** F004 US1 + US2: chat history is stored per conversation and paged back. */
@SpringBootTest(properties = {
        "spring.ai.active=false",
        "app.chat.compaction-threshold=1000",
        "app.chat.history-page-size=20"
})
@ActiveProfiles("fast")
class ChatHistoryIntegrationTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 21);

    @Autowired private WebApplicationContext context;
    @Autowired private UserService userService;
    @Autowired private DefaultRoutineRepository defaultRoutineRepository;
    @Autowired private ChatConversationRepository conversationRepository;
    @MockitoSpyBean private AiChatService aiChatService;

    private final ObjectMapper json = new ObjectMapper();
    private MockMvc mvc;
    private User user;
    private DefaultRoutine routine;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        user = newUser();
        routine = defaultRoutineRepository.findByUserAndRoutineType(user, DefaultRoutine.RoutineTypeEnum.DAILY).orElseThrow();
    }

    @AfterEach
    void resetSpy() {
        reset(aiChatService);
    }

    // ---------- US1 ----------

    @Test
    void emptyHistoryBeforeAnyChatAndNoRowCreated() throws Exception {
        JsonNode page = getJson(routineHistoryUrl(user.getId(), routine.getId()));
        assertEquals(0, page.get("messages").size());
        assertFalse(page.get("hasMore").asBoolean());
        assertTrue(page.get("nextBefore").isNull());
        assertTrue(conversationRepository.findByUser_IdAndTypeAndDefaultRoutineId(
                user.getId(), ChatConversationType.DEFAULT_ROUTINE, routine.getId()).isEmpty());
    }

    @Test
    void defaultRoutineExchangesAreStoredInOrder() throws Exception {
        for (int i = 1; i <= 3; i++) {
            sendRoutine("m" + i).andExpect(status().isOk());
        }
        JsonNode msgs = getJson(routineHistoryUrl(user.getId(), routine.getId())).get("messages");
        assertEquals(6, msgs.size());
        for (int i = 0; i < 6; i++) {
            assertEquals(i + 1, msgs.get(i).get("seq").asLong());
            assertEquals(i % 2 == 0 ? "user" : "assistant", msgs.get(i).get("role").asText());
        }
        assertEquals("m1", msgs.get(0).get("content").asText());
        assertEquals("m3", msgs.get(4).get("content").asText());
        assertFalse(msgs.get(0).has("summary"));
    }

    @Test
    void dailyConversationsAreSeparatePerDateAndFromRoutine() throws Exception {
        sendRoutine("routine msg").andExpect(status().isOk());
        sendDaily(DAY, "day one").andExpect(status().isOk());
        sendDaily(DAY, "day two").andExpect(status().isOk());

        JsonNode day = getJson(dailyHistoryUrl(user.getId(), DAY)).get("messages");
        assertEquals(4, day.size());
        assertEquals("day one", day.get(0).get("content").asText());

        assertEquals(0, getJson(dailyHistoryUrl(user.getId(), DAY.plusDays(1))).get("messages").size());
        assertEquals(2, getJson(routineHistoryUrl(user.getId(), routine.getId())).get("messages").size());
    }

    @Test
    void blankMessageIsRejectedAndNotStored() throws Exception {
        sendRoutine("   ").andExpect(status().isBadRequest());
        sendDaily(DAY, "").andExpect(status().isBadRequest());
        assertEquals(0, getJson(routineHistoryUrl(user.getId(), routine.getId())).get("messages").size());
        assertEquals(0, getJson(dailyHistoryUrl(user.getId(), DAY)).get("messages").size());
    }

    @Test
    void missingDateIsRejected() throws Exception {
        mvc.perform(post("/api/ai/chat/daily-routine/" + user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("message", "hi"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownUserIs404() throws Exception {
        UUID unknown = UUID.randomUUID();
        mvc.perform(get(routineHistoryUrl(unknown, routine.getId()))).andExpect(status().isNotFound());
        mvc.perform(get(dailyHistoryUrl(unknown, DAY))).andExpect(status().isNotFound());
        mvc.perform(post("/api/ai/chat/daily-routine/" + unknown)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("date", DAY.toString(), "message", "hi"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void anotherUsersRoutineIs404() throws Exception {
        User other = newUser();
        DefaultRoutine otherRoutine = defaultRoutineRepository
                .findByUserAndRoutineType(other, DefaultRoutine.RoutineTypeEnum.DAILY).orElseThrow();
        mvc.perform(get(routineHistoryUrl(user.getId(), otherRoutine.getId()))).andExpect(status().isNotFound());
        mvc.perform(post("/api/ai/chat/default-routine/" + user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "defaultRoutine", Map.of("id", otherRoutine.getId().toString()),
                                "message", "hi"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void secondMessageGetsFirstExchangeAsContext() throws Exception {
        sendRoutine("first").andExpect(status().isOk());
        sendRoutine("second").andExpect(status().isOk());

        ArgumentCaptor<ChatContext> captor = ArgumentCaptor.forClass(ChatContext.class);
        verify(aiChatService).defaultRoutineChat(eq(user.getId()), any(), captor.capture(), eq("second"));
        ChatContext ctx = captor.getValue();
        assertNull(ctx.summary());
        assertEquals(2, ctx.recentMessages().size());
        assertTrue(ctx.recentMessages().get(0) instanceof UserMessage);
        assertEquals("first", ctx.recentMessages().get(0).getText());
        assertTrue(ctx.recentMessages().get(1) instanceof AssistantMessage);
    }

    @Test
    void failedAiCallStoresNothing() throws Exception {
        doThrow(new RuntimeException("AI down"))
                .when(aiChatService).dailyRoutineChat(any(), any(), any(), any(), anyString());
        sendDaily(DAY, "will fail").andExpect(status().is5xxServerError());
        assertEquals(0, getJson(dailyHistoryUrl(user.getId(), DAY)).get("messages").size());
    }

    // ---------- US2 ----------

    @Test
    void historyPagesBackwardsWithoutGapsOrDuplicates() throws Exception {
        for (int i = 1; i <= 15; i++) {
            sendDaily(DAY, "q" + i).andExpect(status().isOk());
        }
        String url = dailyHistoryUrl(user.getId(), DAY);

        JsonNode newest = getJson(url);
        assertEquals(20, newest.get("messages").size());
        assertEquals(11, newest.get("messages").get(0).get("seq").asLong());
        assertEquals(30, newest.get("messages").get(19).get("seq").asLong());
        assertTrue(newest.get("hasMore").asBoolean());
        assertEquals(11, newest.get("nextBefore").asLong());

        JsonNode older = getJson(url + "?before=11");
        assertEquals(10, older.get("messages").size());
        assertEquals(1, older.get("messages").get(0).get("seq").asLong());
        assertFalse(older.get("hasMore").asBoolean());
        assertTrue(older.get("nextBefore").isNull());

        List<Long> all = new ArrayList<>();
        older.get("messages").forEach(m -> all.add(m.get("seq").asLong()));
        newest.get("messages").forEach(m -> all.add(m.get("seq").asLong()));
        for (int i = 0; i < 30; i++) {
            assertEquals(i + 1, all.get(i));
        }

        assertEquals(20, getJson(url + "?limit=500").get("messages").size());

        JsonNode small = getJson(url + "?limit=5&before=11");
        assertEquals(5, small.get("messages").size());
        assertEquals(6, small.get("messages").get(0).get("seq").asLong());
        assertTrue(small.get("hasMore").asBoolean());
    }

    // ---------- helpers ----------

    private User newUser() {
        User u = new User();
        String tag = UUID.randomUUID().toString().substring(0, 8);
        u.setUsername("chat-" + tag);
        u.setEmail("chat-" + tag + "@example.test");
        return userService.createUserAndDefaultRoutine(u);
    }

    private ResultActions sendRoutine(String message) throws Exception {
        return mvc.perform(post("/api/ai/chat/default-routine/" + user.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                        "defaultRoutine", Map.of("id", routine.getId().toString()),
                        "message", message))));
    }

    private ResultActions sendDaily(LocalDate date, String message) throws Exception {
        return mvc.perform(post("/api/ai/chat/daily-routine/" + user.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("date", date.toString(), "message", message))));
    }

    private JsonNode getJson(String url) throws Exception {
        String body = mvc.perform(get(url)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private static String routineHistoryUrl(UUID userId, UUID routineId) {
        return "/api/ai/chat/default-routine/" + userId + "/" + routineId + "/messages";
    }

    private static String dailyHistoryUrl(UUID userId, LocalDate date) {
        return "/api/ai/chat/daily-routine/" + userId + "/" + date + "/messages";
    }
}
