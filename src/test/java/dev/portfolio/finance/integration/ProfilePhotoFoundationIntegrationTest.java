package dev.portfolio.finance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static dev.portfolio.finance.support.ProfilePhotoTestSupport.*;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.repository.UserRepository;
import dev.portfolio.finance.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"app.profile-photo.enabled=true", "app.profile-photo.cloud-name=test-cloud",
        "app.profile-photo.api-key=test-api-key", "app.profile-photo.api-secret=test-api-secret",
        "app.profile-photo.key-prefix=fintrack/test/profile-photos"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfilePhotoFoundationIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private JwtService jwt;

    @Test
    void canonicalReadAndAccountUpdatesPreservePhotoWithoutExposingKeyProperty() throws Exception {
        User user = new User("First", "Last", "photo-foundation@example.com", "secret-hash");
        user.changeProfilePhotoKey(KEY);
        users.saveAndFlush(user);
        String token = jwt.generateToken(user);
        for (var request : java.util.List.of(
                get("/api/auth/me"),
                put("/api/account/profile").contentType("application/json")
                        .content("{\"firstName\":\"New\",\"lastName\":\"Name\",\"displayName\":\"Display\"}"),
                put("/api/account/preferences").contentType("application/json")
                        .content("{\"dateFormat\":\"ISO\",\"transactionPageSize\":25}"))) {
            String response = mvc.perform(request.header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.profilePhotoUrl").value(URL))
                    .andExpect(jsonPath("$.profilePhotoKey").doesNotExist())
                    .andReturn().getResponse().getContentAsString();
            assertThat(response).doesNotContain("secret-hash", "test-api-key", "test-api-secret", token);
        }
        assertThat(users.findByEmail(user.getEmail()).orElseThrow().getProfilePhotoKey()).isEqualTo(KEY);
    }
}
