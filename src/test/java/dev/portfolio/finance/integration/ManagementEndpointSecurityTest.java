package dev.portfolio.finance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.repository.UserRepository;
import dev.portfolio.finance.security.JwtService;
import dev.portfolio.finance.security.RefreshCookieService;

/**
 * Monitoring endpoints (issue #20) with the default and test configuration: public health is
 * the status alone, and everything more detailed needs a valid access token. Production
 * exposes even less; see {@link ProductionManagementConfigTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ManagementEndpointSecurityTest {

    /** Words that would mean configuration, credentials, or internals leaked into public health. */
    private static final List<String> NEVER_PUBLIC = List.of("components", "details", "db", "jdbc", "h2",
            "url", "username", "password", "secret", "diskSpace", "version", "java", "host", "error");

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper jsonMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;
    @Autowired private RefreshCookieService refreshCookieService;
    @Autowired private MeterRegistry meterRegistry;
    @Value("${app.jwt.secret}") private String jwtSecret;

    private String token;
    private User user;

    @BeforeEach
    void signIn() {
        user = userRepository.save(new User("Monitor", "Test", "monitor-" + UUID.randomUUID() + "@example.com",
                passwordEncoder.encode("Meadow river lantern 73!")));
        token = jwtService.generateToken(user);
    }

    // ------------------------------------------------------------------ public health

    @Test
    void keepsTheExistingApiHealthContract() throws Exception {
        JsonNode body = json(mockMvc.perform(get("/api/health")).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString());

        assertThat(body.propertyNames()).containsExactlyInAnyOrder("status", "application");
        assertThat(body.get("status").asString()).isEqualTo("ok");
        assertThat(body.get("application").asString()).isEqualTo("Finance Operations Dashboard");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/health/liveness", "/actuator/health/readiness"})
    void publicHealthIsTheStatusAloneWithoutAnyCredentials(String path) throws Exception {
        String raw = mockMvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(json(raw).propertyNames()).containsExactly("status");
        assertThat(json(raw).get("status").asString()).isEqualTo("UP");
        assertThat(raw.toLowerCase()).doesNotContain(NEVER_PUBLIC.stream().map(String::toLowerCase).toList());
    }

    @Test
    void aRefreshCookieAddsNothingToPublicHealthAndUnlocksNothingElse() throws Exception {
        Cookie refresh = new Cookie(refreshCookieService.cookieName(), "some-refresh-token-value");

        String raw = mockMvc.perform(get("/actuator/health/readiness").cookie(refresh))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json(raw).propertyNames()).containsExactly("status");

        for (String path : List.of("/actuator/health", "/actuator/metrics", "/actuator/info", "/actuator/health/db")) {
            mockMvc.perform(get(path).cookie(refresh))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        }
    }

    // ------------------------------------------------------------------ protected endpoints

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/health", "/actuator/metrics", "/actuator/metrics/jvm.memory.used",
            "/actuator/info", "/actuator/health/db", "/actuator"})
    void protectedManagementEndpointsNeedAnAccessToken(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void anInvalidAccessTokenGetsNothing() throws Exception {
        mockMvc.perform(get("/actuator/metrics").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/health").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anExpiredAccessTokenGetsNothing() throws Exception {
        Clock anHourAgo = Clock.fixed(Instant.now().minus(Duration.ofHours(1)), ZoneOffset.UTC);
        String expired = new JwtService(jwtSecret, Duration.ofMinutes(5), anHourAgo).generateToken(user);

        mockMvc.perform(get("/actuator/metrics").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCESS_TOKEN_EXPIRED"));
    }

    @Test
    void aValidAccessTokenSeesComponentStatusWithoutCredentialsOrConnectionDetails() throws Exception {
        String raw = mockMvc.perform(get("/actuator/health").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode db = json(raw).get("components").get("db");

        assertThat(db.get("status").asString()).isEqualTo("UP");
        assertThat(db.get("details").get("database").asString()).isEqualTo("H2");
        assertThat(raw.toLowerCase()).doesNotContain("jdbc:", "password", "username", "\"sa\"", "fintrack_test", "secret");
        assertThat(json(raw).get("components").has("diskSpace")).isFalse(); // Disabled on purpose.
        assertThat(json(raw).get("components").has("livenessState")).isTrue();
        assertThat(json(raw).get("components").has("readinessState")).isTrue();
    }

    @Test
    void aValidAccessTokenSeesMetricsIncludingTheConnectionPool() throws Exception {
        String raw = mockMvc.perform(get("/actuator/metrics").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> names = json(raw).get("names").valueStream().map(JsonNode::asString).toList();

        assertThat(names).contains("jvm.memory.used", "http.server.requests", "hikaricp.connections.active",
                "hikaricp.connections.max");
    }

    @ParameterizedTest
    @ValueSource(strings = {"env", "configprops", "beans", "mappings", "heapdump", "threaddump", "loggers",
            "shutdown", "scheduledtasks", "caches", "conditions", "startup", "sessions", "prometheus"})
    void everyOtherManagementEndpointIsNotThere(String endpoint) throws Exception {
        // Signed out: the usual 401, which says nothing about whether the endpoint exists.
        mockMvc.perform(get("/actuator/" + endpoint)).andExpect(status().isUnauthorized());
        // Signed in: simply not found.
        mockMvc.perform(get("/actuator/" + endpoint).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void theManagementIndexIsNotPublishedEvenWhenSignedIn() throws Exception {
        mockMvc.perform(get("/actuator").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void ordinaryApisKeepTheirAuthenticationRules() throws Exception {
        mockMvc.perform(get("/api/transactions")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/budgets")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/budgets").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andExpect(status().isOk());
    }

    @Test
    void metricsStayInMemoryWithNoMonitoringServiceConfigured() {
        // Only Micrometer's in-memory registry: nothing is sent anywhere.
        List<MeterRegistry> registries = meterRegistry instanceof CompositeMeterRegistry composite
                ? List.copyOf(composite.getRegistries()) : List.of(meterRegistry);
        assertThat(registries).isNotEmpty().allSatisfy(registry -> assertThat(registry).isInstanceOf(SimpleMeterRegistry.class));
        assertThat(meterRegistry.find("hikaricp.connections").gauge()).isNotNull();
    }

    private JsonNode json(String raw) {
        return jsonMapper.readTree(raw);
    }
}
