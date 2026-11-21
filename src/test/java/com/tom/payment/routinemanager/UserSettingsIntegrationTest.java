package com.tom.payment.routinemanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tom.payment.routinemanager.model.User;
import com.tom.payment.routinemanager.model.UserSetting;
import com.tom.payment.routinemanager.repository.UserSettingRepository;
import com.tom.payment.routinemanager.service.UserService;

/** F006: account-level UI settings (contracts/settings-api.md). */
@SpringBootTest(properties = "spring.ai.active=false")
@ActiveProfiles("fast")
class UserSettingsIntegrationTest {

    @Autowired private WebApplicationContext context;
    @Autowired private UserService userService;
    @Autowired private UserSettingRepository settingRepository;

    private final ObjectMapper json = new ObjectMapper();
    private MockMvc mvc;
    private User user;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        user = newUser();
    }

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    // ---------- US1: settings stay as the user left them ----------

    @Test
    void newUserGetsDefaultsAndNoRows() throws Exception {
        JsonNode body = getSettings(user.getId());
        assertEquals("SYSTEM", body.at("/settings/theme").asText());
        assertEquals(1.0, body.at("/settings/boardZoom").asDouble());
        assertEquals(0, body.get("customized").size());
        assertTrue(settingRepository.findByUserId(user.getId()).isEmpty());
    }

    @Test
    void patchThemeIsStoredUpperCaseAndMarkedCustomized() throws Exception {
        JsonNode patched = readJson(patchSettings(user.getId(), "{\"theme\":\"dark\"}").andExpect(status().isOk()));
        assertEquals("DARK", patched.at("/settings/theme").asText());
        assertTrue(customized(patched).contains("theme"));

        JsonNode again = getSettings(user.getId());
        assertEquals("DARK", again.at("/settings/theme").asText());
        assertEquals(List.of("theme"), customized(again));
    }

    @Test
    void patchingOneKeyLeavesTheOthers() throws Exception {
        patchSettings(user.getId(), "{\"boardZoom\":1.5}").andExpect(status().isOk());
        JsonNode body = readJson(patchSettings(user.getId(), "{\"theme\":\"LIGHT\"}").andExpect(status().isOk()));
        assertEquals(1.5, body.at("/settings/boardZoom").asDouble());
        assertEquals("LIGHT", body.at("/settings/theme").asText());
        assertEquals(2, body.get("customized").size());
    }

    @Test
    void invalidPatchesAreRejectedAndWriteNothing() throws Exception {
        patchSettings(user.getId(), "{\"theme\":\"LIGHT\"}").andExpect(status().isOk());
        for (String bad : List.of(
                "{}",
                "{\"theme\":\"BLUE\"}",
                "{\"theme\":3}",
                "{\"boardZoom\":0.25}",
                "{\"boardZoom\":3.25}",
                "{\"boardZoom\":1.1}",
                "{\"boardZoom\":\"1.5\"}",
                "{\"fontSize\":14}",
                "{\"theme\":\"DARK\",\"boardZoom\":9}")) {
            patchSettings(user.getId(), bad).andExpect(status().isBadRequest());
        }
        JsonNode body = getSettings(user.getId());
        assertEquals("LIGHT", body.at("/settings/theme").asText());
        assertEquals(1.0, body.at("/settings/boardZoom").asDouble());
        assertEquals(List.of("theme"), customized(body));
    }

    @Test
    void unknownUserIsNotFound() throws Exception {
        UUID missing = UUID.randomUUID();
        mvc.perform(get(settingsUrl(missing))).andExpect(status().isNotFound());
        patchSettings(missing, "{\"theme\":\"DARK\"}").andExpect(status().isNotFound());
    }

    @Test
    void signedInUserCannotReadOrChangeAnotherUsersSettings() throws Exception {
        User other = newUser();
        patchSettings(other.getId(), "{\"theme\":\"DARK\"}").andExpect(status().isOk());

        signInAs(user);
        mvc.perform(get(settingsUrl(other.getId()))).andExpect(status().isForbidden());
        patchSettings(other.getId(), "{\"theme\":\"LIGHT\"}").andExpect(status().isForbidden());
        mvc.perform(get(settingsUrl(user.getId()))).andExpect(status().isOk());

        SecurityContextHolder.clearContext();
        assertEquals("DARK", getSettings(other.getId()).at("/settings/theme").asText());
    }

    // ---------- US2: profile ----------

    @Test
    void profileShowsAccountAndFreePlanWithoutSecrets() throws Exception {
        JsonNode body = readJson(mvc.perform(get(profileUrl(user.getId()))).andExpect(status().isOk()));
        assertEquals(user.getId().toString(), body.get("id").asText());
        assertEquals(user.getUsername(), body.get("username").asText());
        assertEquals(user.getEmail(), body.get("email").asText());
        assertEquals("FREE", body.at("/plan/code").asText());
        assertEquals("Free plan", body.at("/plan/name").asText());
        assertFalse(body.has("passwordHash"));
        assertFalse(body.has("defaultRoutines"));
    }

    @Test
    void profileOfUnknownUserIsNotFoundAndOfAnotherUserIsForbidden() throws Exception {
        mvc.perform(get(profileUrl(UUID.randomUUID()))).andExpect(status().isNotFound());
        User other = newUser();
        signInAs(user);
        mvc.perform(get(profileUrl(other.getId()))).andExpect(status().isForbidden());
        mvc.perform(get(profileUrl(user.getId()))).andExpect(status().isOk());
    }

    // ---------- US3: room for new preferences ----------

    @Test
    void unknownStoredKeysAreIgnored() throws Exception {
        settingRepository.save(new UserSetting(user.getId(), "fontSize", "14", OffsetDateTime.now()));
        JsonNode body = getSettings(user.getId());
        assertFalse(body.get("settings").has("fontSize"));
        assertFalse(customized(body).contains("fontSize"));
        assertEquals("SYSTEM", body.at("/settings/theme").asText());
    }

    @Test
    void corruptStoredValueFallsBackToDefaultAndCanBeOverwritten() throws Exception {
        settingRepository.save(new UserSetting(user.getId(), "boardZoom", "banana", OffsetDateTime.now()));
        JsonNode body = getSettings(user.getId());
        assertEquals(1.0, body.at("/settings/boardZoom").asDouble());
        assertFalse(customized(body).contains("boardZoom"));

        JsonNode fixed = readJson(patchSettings(user.getId(), "{\"boardZoom\":2.0}").andExpect(status().isOk()));
        assertEquals(2.0, fixed.at("/settings/boardZoom").asDouble());
        assertTrue(customized(fixed).contains("boardZoom"));
    }

    // ---------- helpers ----------

    static String profileUrl(UUID userId) {
        return "/api/v1/users/" + userId + "/profile";
    }


    private User newUser() {
        User u = new User();
        String tag = UUID.randomUUID().toString().substring(0, 8);
        u.setUsername("settings-" + tag);
        u.setEmail("settings-" + tag + "@example.test");
        return userService.createUserAndDefaultRoutine(u);
    }

    /** CurrentUserGuard compares the authenticated name with the user's email. */
    private static void signInAs(User u) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(u.getEmail(), null, List.of()));
    }

    private ResultActions patchSettings(UUID userId, String body) throws Exception {
        return mvc.perform(patch(settingsUrl(userId)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode getSettings(UUID userId) throws Exception {
        return readJson(mvc.perform(get(settingsUrl(userId))).andExpect(status().isOk()));
    }

    private JsonNode readJson(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static List<String> customized(JsonNode body) {
        assertFalse(body.get("customized") == null, "customized missing");
        return json(body.get("customized"));
    }

    private static List<String> json(JsonNode array) {
        return java.util.stream.StreamSupport.stream(array.spliterator(), false).map(JsonNode::asText).sorted().toList();
    }

    static String settingsUrl(UUID userId) {
        return "/api/v1/users/" + userId + "/settings";
    }
}
