package dev.portfolio.finance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A database that is down, simulated by replacing the database health check (no real outage):
 * readiness reports DOWN with a 503 and nothing else, while liveness and the
 * long-standing /api/health stay up because the process itself is fine.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ManagementDatabaseDownTest {

    @TestConfiguration
    static class DatabaseDown {
        /** Takes the place of Spring Boot's own database check (same bean name). */
        @Bean
        HealthIndicator dbHealthIndicator() {
            return () -> Health.down()
                    .withDetail("error", "Connection refused: jdbc:mysql://private-host:3306 user=PRIVATE")
                    .build();
        }
    }

    @Autowired private MockMvc mockMvc;

    @Test
    void readinessReportsDownWithoutAnyDetail() throws Exception {
        String raw = mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andReturn().getResponse().getContentAsString();

        assertThat(raw).isEqualTo("{\"status\":\"DOWN\"}");
        assertThat(raw).doesNotContain("jdbc", "private-host", "PRIVATE", "Connection refused");
    }

    @Test
    void theRootHealthEndpointStillNeedsAnAccessToken() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isUnauthorized());
    }

    @Test
    void livenessAndTheApiHealthEndpointStayUp() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }
}
