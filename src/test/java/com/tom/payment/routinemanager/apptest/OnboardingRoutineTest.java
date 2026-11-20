package com.tom.payment.routinemanager.apptest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.tom.payment.routinemanager.model.DefaultRoutine;
import com.tom.payment.routinemanager.model.RoutineTaskTemplate;
import com.tom.payment.routinemanager.model.User;
import com.tom.payment.routinemanager.repository.DefaultRoutineRepository;
import com.tom.payment.routinemanager.repository.UserRepository;
import com.tom.payment.routinemanager.service.DefaultRoutineService;
import com.tom.payment.routinemanager.service.UserService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@SpringBootTest
public class OnboardingRoutineTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DefaultRoutineService defaultRoutineService;

    @Autowired
    private DefaultRoutineRepository defaultRoutineRepository;

    @BeforeEach
    void setup() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void onboardingWorkflowCreatesUserAndDefaultRoutineWithTasks() throws Exception {
        Map<String, Object> newUser = Map.of(
                "username", "onboarding-user",
                "email", "onboarding@example.com",
                "passwordHash", "hashed-password"
        );

        MvcResult createUserResult = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newUser)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode createdUser = objectMapper.readTree(createUserResult.getResponse().getContentAsString());
        assertNotNull(createdUser);
        String userId = createdUser.path("id").asText();
        assertNotNull(userId);

        MvcResult defaultRoutineResult = mockMvc.perform(get("/api/routine-manager/default-routine/{userId}", userId))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode defaultRoutine = objectMapper.readTree(defaultRoutineResult.getResponse().getContentAsString());
        assertNotNull(defaultRoutine);
        assertEquals("Default Routine", defaultRoutine.path("name").asText());
        String routineId = defaultRoutine.path("id").asText();
        assertNotNull(routineId);

        RoutineTaskTemplate taskOne = new RoutineTaskTemplate();
        taskOne.setName("Morning Stretch");
        taskOne.setDescription("Light stretching routine");
        taskOne.setStartTime(LocalTime.of(7, 0));
        taskOne.setDurationMinutes(15);

        RoutineTaskTemplate taskTwo = new RoutineTaskTemplate();
        taskTwo.setName("Plan Day");
        taskTwo.setDescription("Review priorities for the day");
        taskTwo.setStartTime(LocalTime.of(8, 0));
        taskTwo.setDurationMinutes(30);

        MvcResult addTasksResult = mockMvc.perform(post("/api/routine-manager/default-routine/{routineId}/tasks", routineId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(taskOne, taskTwo))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode createdTasks = objectMapper.readTree(addTasksResult.getResponse().getContentAsString());
        assertNotNull(createdTasks);
        assertEquals(2, createdTasks.size());
        assertEquals("Morning Stretch", createdTasks.get(0).path("name").asText());
        assertEquals("Plan Day", createdTasks.get(1).path("name").asText());
    }

    @Test
    void addDefaultTasksAcceptsSingleTaskObjectPayload() throws Exception {
        Map<String, Object> newUser = Map.of(
                "username", "single-payload-user",
                "email", "single-payload@example.com",
                "passwordHash", "hashed-password"
        );

        MvcResult createUserResult = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newUser)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode createdUser = objectMapper.readTree(createUserResult.getResponse().getContentAsString());
        String userId = createdUser.path("id").asText();
        assertNotNull(userId);

        MvcResult defaultRoutineResult = mockMvc.perform(get("/api/routine-manager/default-routine/{userId}", userId))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode defaultRoutine = objectMapper.readTree(defaultRoutineResult.getResponse().getContentAsString());
        String routineId = defaultRoutine.path("id").asText();
        assertNotNull(routineId);

        RoutineTaskTemplate task = new RoutineTaskTemplate();
        task.setName("Single Task");
        task.setDescription("Accepts a single object payload");
        task.setStartTime(LocalTime.of(9, 0));
        task.setDurationMinutes(20);

        MvcResult addTasksResult = mockMvc.perform(post("/api/routine-manager/default-routine/{routineId}/tasks", routineId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(task)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode createdTasks = objectMapper.readTree(addTasksResult.getResponse().getContentAsString());
        assertNotNull(createdTasks);
        assertEquals(1, createdTasks.size());
        assertEquals("Single Task", createdTasks.get(0).path("name").asText());
    }

    // --- 002-onboarding-instruction: one default routine per type (research R2, contracts/default-routine-api.md) ---

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    private JsonNode getRoutine(String userId, String typeOrNull) throws Exception {
        var request = get("/api/v1/default-routine/users/{userId}", userId);
        if (typeOrNull != null) {
            request = request.param("type", typeOrNull);
        }
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void registrationCreatesEmptyWeekdayAndWeekendRoutines() throws Exception {
        String email = uniqueEmail("two-routines");
        User user = userService.createUserAndDefaultRoutine(new User(email, email));

        long routines = defaultRoutineRepository.findAll().stream()
                .filter(routine -> routine.getUser().getId().equals(user.getId()))
                .count();
        assertEquals(2, routines);

        JsonNode weekday = getRoutine(user.getId().toString(), "DAILY");
        assertEquals("Weekday Routine", weekday.path("name").asText());
        assertEquals("DAILY", weekday.path("routineType").asText());
        assertEquals(0, weekday.path("tasks").size());

        JsonNode weekend = getRoutine(user.getId().toString(), "WEEKEND");
        assertEquals("Weekend Routine", weekend.path("name").asText());
        assertEquals("WEEKEND", weekend.path("routineType").asText());
        assertEquals(0, weekend.path("tasks").size());
    }

    @Test
    void weekendRoutineIsCreatedOnFirstRequestAndReusedAfterwards() throws Exception {
        // An existing user from before this feature: only a weekday routine.
        String email = uniqueEmail("legacy-user");
        User user = userRepository.save(new User(email, email));
        DefaultRoutine weekdayOnly = new DefaultRoutine();
        weekdayOnly.setName("Default Routine");
        weekdayOnly.setRoutineType(DefaultRoutine.RoutineTypeEnum.DAILY);
        defaultRoutineService.createDefaultRoutine(user.getId(), weekdayOnly);
        assertTrue(defaultRoutineRepository
                .findByUserAndRoutineType(user, DefaultRoutine.RoutineTypeEnum.WEEKEND).isEmpty());

        JsonNode first = getRoutine(user.getId().toString(), "WEEKEND");
        JsonNode second = getRoutine(user.getId().toString(), "WEEKEND");

        assertEquals("WEEKEND", first.path("routineType").asText());
        assertEquals(0, first.path("tasks").size());
        assertEquals(first.path("id").asText(), second.path("id").asText());
        assertEquals(first.path("id").asText(),
                defaultRoutineService.getOrCreateDefaultRoutine(user.getId(), DefaultRoutine.RoutineTypeEnum.WEEKEND)
                        .getId().toString());
    }

    @Test
    void routineTypeDefaultsToWeekday() throws Exception {
        String email = uniqueEmail("default-type");
        User user = userService.createUserAndDefaultRoutine(new User(email, email));
        assertEquals("DAILY", getRoutine(user.getId().toString(), null).path("routineType").asText());
    }

    @Test
    void unknownRoutineTypeIsRejected() throws Exception {
        String email = uniqueEmail("bad-type");
        User user = userService.createUserAndDefaultRoutine(new User(email, email));
        mockMvc.perform(get("/api/v1/default-routine/users/{userId}", user.getId()).param("type", "FOO"))
                .andExpect(status().isBadRequest());
    }
}
