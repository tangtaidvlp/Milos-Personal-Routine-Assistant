package com.tom.payment.routinemanager.component;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.tom.payment.routinemanager.support.BaseComponentTest;
import com.tom.payment.routinemanager.support.TestUser;

/**
 * Flow: the app sends the user's local time with every Buddy call, so Buddy can match their time
 * of day (F015, contracts/buddy-local-time.md). A missing or malformed time is ignored, never
 * refused. Windows and wording are in unit/TimeOfDayTest and TimeOfDayPromptTest.
 */
class LocalTimeComponentTest extends BaseComponentTest {

    @Test
    void dailyChatPassesTheUsersLocalTime() throws Exception {
        TestUser user = newUser("time");
        sendDaily(user, "08:05");
        verify(aiChatService).dailyRoutineChat(eq(user.id()), any(), any(), any(), anyString(), eq(LocalTime.of(8, 5)));
    }

    @Test
    void malformedTimeIsIgnoredAndTheChatStillWorks() throws Exception {
        TestUser user = newUser("time");
        sendDaily(user, "25:99");
        verify(aiChatService).dailyRoutineChat(eq(user.id()), any(), any(), any(), anyString(), isNull());
    }

    @Test
    void defaultRoutineChatWithoutATimeGetsNone() throws Exception {
        TestUser user = newUser("time");
        String routineId = read(mvc.perform(as(user, get("/api/v1/default-routine/users/" + user.id())))
                .andExpect(status().isOk())).get("id").asText();

        mvc.perform(as(user, post("/api/ai/chat/default-routine/" + user.id()),
                        Map.of("defaultRoutine", Map.of("id", routineId), "message", "plan my week")))
                .andExpect(status().isOk());
        verify(aiChatService).defaultRoutineChat(eq(user.id()), any(), any(), anyString(), isNull());
    }

    @Test
    void reflectionChatAndNotePassTheUsersLocalTime() throws Exception {
        TestUser user = newUser("time");
        LocalDate today = clock.today();
        String reflection = "/api/routine-manager/daily-reflection/" + user.id() + "/" + today;
        mvc.perform(as(user, post(reflection))).andExpect(status().isOk());

        mvc.perform(as(user, post("/api/ai/chat/daily-reflection/" + user.id()),
                        Map.of("date", today.toString(), "message", "I slept in", "localTime", "21:10")))
                .andExpect(status().isOk());
        verify(reflectionAi).chat(eq(user.id()), eq(today), any(), any(), anyString(), eq(LocalTime.of(21, 10)));

        mvc.perform(as(user, post(reflection + "/feedback").param("localTime", "07:30"))).andExpect(status().isOk());
        verify(reflectionAi).feedback(eq(user.id()), any(), eq(LocalTime.of(7, 30)));
    }

    private void sendDaily(TestUser user, String localTime) throws Exception {
        mvc.perform(as(user, post("/api/ai/chat/daily-routine/" + user.id()),
                        Map.of("date", clock.today().toString(), "message", "what now?", "localTime", localTime)))
                .andExpect(status().isOk());
    }
}
