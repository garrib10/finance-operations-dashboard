package dev.portfolio.finance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.repository.UserRepository;
import dev.portfolio.finance.security.JwtService;

/**
 * The production profile (test database, prod settings): any registered user can sign in and
 * there is no operator role, so even a valid access token sees only the health status, and
 * metrics and info are not exposed at all.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "prod"})
class ProductionManagementConfigTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private Environment environment;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;

    private String signedInToken() {
        User user = userRepository.save(new User("Prod", "Monitor", "prod-monitor-" + UUID.randomUUID() + "@example.com",
                passwordEncoder.encode("Meadow river lantern 73!")));
        return "Bearer " + jwtService.generateToken(user);
    }

    @Test
    void exposesOnlyHealthWithStatusAloneAndNoDetails() {
        assertThat(environment.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health");
        assertThat(environment.getProperty("management.endpoint.health.show-details")).isEqualTo("never");
        assertThat(environment.getProperty("management.endpoint.health.show-components")).isEqualTo("never");
        assertThat(environment.getProperty("management.endpoint.metrics.access")).isEqualTo("none");
        assertThat(environment.getProperty("management.endpoint.info.access")).isEqualTo("none");
        assertThat(environment.getProperty("management.endpoints.access.default")).isEqualTo("none");
        assertThat(environment.getProperty("management.endpoints.web.exposure.include")).doesNotContain("*");
    }

    @Test
    void evenASignedInUserSeesOnlyTheStatus() throws Exception {
        String raw = mockMvc.perform(get("/actuator/health").header(HttpHeaders.AUTHORIZATION, signedInToken()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        // The status and the two probe group names: no components, details, or database.
        assertThat(raw).isEqualTo("{\"groups\":[\"liveness\",\"readiness\"],\"status\":\"UP\"}");
        mockMvc.perform(get("/actuator/health/db").header(HttpHeaders.AUTHORIZATION, signedInToken()))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/health/liveness", "/actuator/health/readiness"})
    void publicHealthAndProbesStayAvailable(String path) throws Exception {
        String raw = mockMvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(raw).isEqualTo("{\"status\":\"UP\"}");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/metrics", "/actuator/info", "/actuator/env", "/actuator/heapdump", "/actuator"})
    void metricsInfoAndEverythingElseAreAbsentEvenWhenSignedIn(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, signedInToken())).andExpect(status().isNotFound());
    }

    @Test
    void keepsTheApiHealthEndpoint() throws Exception {
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());
    }
}
