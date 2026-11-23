package com.tom.payment.routinemanager.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.tom.payment.routinemanager.model.UserSetting;
import com.tom.payment.routinemanager.repository.UserSettingRepository;
import com.tom.payment.routinemanager.support.BaseComponentTest;
import com.tom.payment.routinemanager.support.TestUser;

/**
 * Flow: a user reads and changes their account settings and profile (F006).
 * Value-validation edge cases are in unit/UserSettingsRegistryTest.
 */
class UserSettingsComponentTest extends BaseComponentTest {

    @Autowired
    private UserSettingRepository settingRepository;

    @Test
    void newUserGetsDefaultsAndNoRows() throws Exception {
        TestUser user = newUser("settings");
        JsonNode body = getSettings(user, user.id());
        assertEquals("SYSTEM", body.at("/settings/theme").asText());
        assertEquals(1.0, body.at("/settings/boardZoom").asDouble());
        assertEquals(0, body.get("customized").size());
        assertTrue(settingRepository.findByUserId(user.id()).isEmpty());
    }

    @Test
    void patchedThemeIsStoredUpperCaseAndMarkedCustomized() throws Exception {
        TestUser user = newUser("settings");
        JsonNode patched = read(patchSettings(user, user.id(), "{\"theme\":\"dark\"}").andExpect(status().isOk()));
        assertEquals("DARK", patched.at("/settings/theme").asText());
        assertTrue(customized(patched).contains("theme"));

        JsonNode again = getSettings(user, user.id());
        assertEquals("DARK", again.at("/settings/theme").asText());
        assertEquals(List.of("theme"), customized(again));
    }

    @Test
    void patchingOneKeyLeavesTheOthers() throws Exception {
        TestUser user = newUser("settings");
        patchSettings(user, user.id(), "{\"boardZoom\":1.5}").andExpect(status().isOk());
        JsonNode body = read(patchSettings(user, user.id(), "{\"theme\":\"LIGHT\"}").andExpect(status().isOk()));
        assertEquals(1.5, body.at("/settings/boardZoom").asDouble());
        assertEquals("LIGHT", body.at("/settings/theme").asText());
        assertEquals(2, body.get("customized").size());
    }

    @Test
    void invalidPatchIsRefusedAndWritesNothing() throws Exception {
        TestUser user = newUser("settings");
        patchSettings(user, user.id(), "{\"theme\":\"LIGHT\"}").andExpect(status().isOk());

        patchSettings(user, user.id(), "{\"theme\":\"BLUE\"}").andExpect(error(HttpStatus.BAD_REQUEST));
        patchSettings(user, user.id(), "{}").andExpect(error(HttpStatus.BAD_REQUEST));
        // One bad key spoils the whole PATCH: the valid theme is not written either.
        patchSettings(user, user.id(), "{\"theme\":\"DARK\",\"boardZoom\":9}").andExpect(error(HttpStatus.BAD_REQUEST));

        JsonNode body = getSettings(user, user.id());
        assertEquals("LIGHT", body.at("/settings/theme").asText());
        assertEquals(1.0, body.at("/settings/boardZoom").asDouble());
        assertEquals(List.of("theme"), customized(body));
    }

    @Test
    void unknownUserIsNotFound() throws Exception {
        TestUser user = newUser("settings");
        UUID missing = UUID.randomUUID();
        mvc.perform(as(user, get(settingsUrl(missing)))).andExpect(error(HttpStatus.NOT_FOUND));
        patchSettings(user, missing, "{\"theme\":\"DARK\"}").andExpect(error(HttpStatus.NOT_FOUND));
    }

    @Test
    void signedInUserCannotReadOrChangeAnotherUsersSettings() throws Exception {
        TestUser user = newUser("settings");
        TestUser other = newUser("settings");
        patchSettings(other, other.id(), "{\"theme\":\"DARK\"}").andExpect(status().isOk());

        mvc.perform(as(user, get(settingsUrl(other.id())))).andExpect(error(HttpStatus.FORBIDDEN));
        patchSettings(user, other.id(), "{\"theme\":\"LIGHT\"}").andExpect(error(HttpStatus.FORBIDDEN));
        mvc.perform(as(user, get(settingsUrl(user.id())))).andExpect(status().isOk());

        assertEquals("DARK", getSettings(other, other.id()).at("/settings/theme").asText());
    }

    @Test
    void requestWithoutTokenIsNotAuthenticated() throws Exception {
        TestUser user = newUser("settings");
        mvc.perform(get(settingsUrl(user.id()))).andExpect(error(HttpStatus.UNAUTHORIZED));
        mvc.perform(get(profileUrl(user.id()))).andExpect(error(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void profileShowsAccountAndFreePlanWithoutSecrets() throws Exception {
        TestUser user = newUser("settings");
        JsonNode body = read(mvc.perform(as(user, get(profileUrl(user.id())))).andExpect(status().isOk()));
        assertEquals(user.id().toString(), body.get("id").asText());
        assertEquals(user.username(), body.get("username").asText());
        assertEquals(user.email(), body.get("email").asText());
        assertEquals("FREE", body.at("/plan/code").asText());
        assertEquals("Free plan", body.at("/plan/name").asText());
        assertFalse(body.has("passwordHash"));
        assertFalse(body.has("defaultRoutines"));
    }

    @Test
    void profileOfUnknownUserIsNotFoundAndOfAnotherUserIsForbidden() throws Exception {
        TestUser user = newUser("settings");
        TestUser other = newUser("settings");
        mvc.perform(as(user, get(profileUrl(UUID.randomUUID())))).andExpect(error(HttpStatus.NOT_FOUND));
        mvc.perform(as(user, get(profileUrl(other.id())))).andExpect(error(HttpStatus.FORBIDDEN));
    }

    @Test
    void unknownStoredKeysAreIgnored() throws Exception {
        TestUser user = newUser("settings");
        settingRepository.save(new UserSetting(user.id(), "fontSize", "14", OffsetDateTime.now()));
        JsonNode body = getSettings(user, user.id());
        assertFalse(body.get("settings").has("fontSize"));
        assertFalse(customized(body).contains("fontSize"));
        assertEquals("SYSTEM", body.at("/settings/theme").asText());
    }

    @Test
    void corruptStoredValueFallsBackToDefaultAndCanBeOverwritten() throws Exception {
        TestUser user = newUser("settings");
        settingRepository.save(new UserSetting(user.id(), "boardZoom", "banana", OffsetDateTime.now()));
        JsonNode body = getSettings(user, user.id());
        assertEquals(1.0, body.at("/settings/boardZoom").asDouble());
        assertFalse(customized(body).contains("boardZoom"));

        JsonNode fixed = read(patchSettings(user, user.id(), "{\"boardZoom\":2.0}").andExpect(status().isOk()));
        assertEquals(2.0, fixed.at("/settings/boardZoom").asDouble());
        assertTrue(customized(fixed).contains("boardZoom"));
    }

    // ---------- helpers ----------

    private static String settingsUrl(UUID userId) {
        return "/api/v1/users/" + userId + "/settings";
    }

    private static String profileUrl(UUID userId) {
        return "/api/v1/users/" + userId + "/profile";
    }

    private ResultActions patchSettings(TestUser caller, UUID userId, String body) throws Exception {
        return mvc.perform(as(caller, patch(settingsUrl(userId)), body));
    }

    private JsonNode getSettings(TestUser caller, UUID userId) throws Exception {
        return read(mvc.perform(as(caller, get(settingsUrl(userId)))).andExpect(status().isOk()));
    }

    private static List<String> customized(JsonNode body) {
        return StreamSupport.stream(body.get("customized").spliterator(), false)
                .map(JsonNode::asText).sorted().toList();
    }
}
