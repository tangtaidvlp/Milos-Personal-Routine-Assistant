package com.tom.payment.routinemanager.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.JsonNode;
import com.tom.payment.routinemanager.model.DefaultRoutine;
import com.tom.payment.routinemanager.model.User;
import com.tom.payment.routinemanager.repository.DefaultRoutineRepository;
import com.tom.payment.routinemanager.repository.UserRepository;
import com.tom.payment.routinemanager.support.BaseComponentTest;
import com.tom.payment.routinemanager.support.TestUser;

/**
 * Flow: a new user registers and gets an empty weekday and weekend routine to fill in
 * during onboarding (specs/.done.002-onboarding-instruction).
 */
class OnboardingComponentTest extends BaseComponentTest {

    @Autowired private UserRepository userRepository;
    @Autowired private DefaultRoutineRepository defaultRoutineRepository;

    @Test
    void registrationCreatesEmptyWeekdayAndWeekendRoutines() throws Exception {
        TestUser user = newUser("onboarding");
        User stored = userRepository.findById(user.id()).orElseThrow();
        assertTrue(defaultRoutineRepository.findByUserAndRoutineType(stored, DefaultRoutine.RoutineTypeEnum.DAILY).isPresent());
        assertTrue(defaultRoutineRepository.findByUserAndRoutineType(stored, DefaultRoutine.RoutineTypeEnum.WEEKEND).isPresent());

        JsonNode weekday = routine(user, "DAILY");
        assertEquals("Weekday Routine", weekday.get("name").asText());
        assertEquals("DAILY", weekday.get("routineType").asText());
        assertEquals(0, weekday.get("tasks").size());

        JsonNode weekend = routine(user, "WEEKEND");
        assertEquals("Weekend Routine", weekend.get("name").asText());
        assertEquals("WEEKEND", weekend.get("routineType").asText());
        assertEquals(0, weekend.get("tasks").size());
    }

    @Test
    void userFromBeforeWeekendRoutinesGetsOneOnFirstRequestAndKeepsIt() throws Exception {
        TestUser user = newUser("onboarding");
        // Simulate an account created before weekend routines existed.
        User stored = userRepository.findById(user.id()).orElseThrow();
        defaultRoutineRepository.delete(
                defaultRoutineRepository.findByUserAndRoutineType(stored, DefaultRoutine.RoutineTypeEnum.WEEKEND).orElseThrow());

        JsonNode first = routine(user, "WEEKEND");
        JsonNode second = routine(user, "WEEKEND");
        assertEquals("WEEKEND", first.get("routineType").asText());
        assertEquals(0, first.get("tasks").size());
        assertEquals(first.get("id").asText(), second.get("id").asText());
    }

    @Test
    void routineTypeDefaultsToWeekday() throws Exception {
        TestUser user = newUser("onboarding");
        JsonNode routine = read(mvc.perform(as(user, get("/api/v1/default-routine/users/" + user.id())))
                .andExpect(status().isOk()));
        assertEquals("DAILY", routine.get("routineType").asText());
    }

    @Test
    void unknownRoutineTypeIsRefused() throws Exception {
        TestUser user = newUser("onboarding");
        mvc.perform(as(user, get("/api/v1/default-routine/users/" + user.id()).param("type", "FOO")))
                .andExpect(error(HttpStatus.BAD_REQUEST));
    }

    @Test
    void requestWithoutTokenIsNotAuthenticated() throws Exception {
        TestUser user = newUser("onboarding");
        mvc.perform(get("/api/v1/default-routine/users/" + user.id()).param("type", "WEEKEND"))
                .andExpect(error(HttpStatus.UNAUTHORIZED));
    }

    private JsonNode routine(TestUser user, String type) throws Exception {
        return read(mvc.perform(as(user, get("/api/v1/default-routine/users/" + user.id()).param("type", type)))
                .andExpect(status().isOk()));
    }
}
