package dev.portfolio.finance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(properties = "logging.level.org.springframework.web=DEBUG")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfileFoundationIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JsonMapper mapper;

    @Test
    void registrationPersistsDefaultsAndPreservesPasswordWhitespace() throws Exception {
        String password = " River meadow lantern 42! ";
        String email = "profile-foundation@example.com";
        String response = mockMvc.perform(post("/api/auth/register")
                .contentType("application/json")
                .content(mapper.writeValueAsString(new dev.portfolio.finance.dto.auth.RegisterRequest(
                        " First ", " Last ", email, password))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.displayName").value("First Last"))
                .andExpect(jsonPath("$.preferences.dateFormat").value("MEDIUM"))
                .andExpect(jsonPath("$.preferences.transactionPageSize").value(10))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        User saved = users.findByEmail(email).orElseThrow();
        assertThat(encoder.matches(password, saved.getPasswordHash())).isTrue();
        assertThat(encoder.matches(password.trim(), saved.getPasswordHash())).isFalse();
        assertThat(response).doesNotContain(password, saved.getPasswordHash());
        String login = mockMvc.perform(post("/api/auth/login").contentType("application/json")
                .content(mapper.writeValueAsString(new dev.portfolio.finance.dto.auth.LoginRequest(email, password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = mapper.readTree(login).get("accessToken").stringValue();
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().json(response));
    }

    @Test
    void acceptsExistingPasswordBelowNewMinimum() throws Exception {
        String password = "old12345";
        String hash = encoder.encode(password);
        users.saveAndFlush(new User("Legacy", "User", "legacy-profile@example.com", hash));
        mockMvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"legacy-profile@example.com\",\"password\":\"old12345\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isString());
        assertThat(users.findByEmail("legacy-profile@example.com").orElseThrow().getPasswordHash()).isEqualTo(hash);
    }

    @Test
    void returnsUsefulPasswordErrorWithoutSubmittedValue(CapturedOutput output) throws Exception {
        String password = "abcdefghijklmn";
        String response = mockMvc.perform(post("/api/auth/register").contentType("application/json")
                .content(mapper.writeValueAsString(new dev.portfolio.finance.dto.auth.RegisterRequest(
                        "First", "Last", "invalid-profile@example.com", password))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.password").value("Password must contain at least 15 characters"))
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain(password);
        assertThat(output.getAll()).doesNotContain(password);
        assertThat(users.existsByEmail("invalid-profile@example.com")).isFalse();
    }
}
