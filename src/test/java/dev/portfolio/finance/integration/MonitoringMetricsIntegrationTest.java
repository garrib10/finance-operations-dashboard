package dev.portfolio.finance.integration;

import static dev.portfolio.finance.support.AuthRequests.protectedAuth;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.monitoring.AuthenticationMetrics;
import dev.portfolio.finance.repository.UserRepository;
import dev.portfolio.finance.security.JwtService;
import dev.portfolio.finance.security.RefreshCookieService;
import dev.portfolio.finance.support.MutableClock;

/**
 * Issue #20, Phase 2 metrics: the built-in HTTP timer with bounded tags, the authentication
 * failure counter with fixed tags counted once per failure, and the connection-pool meters.
 * No tag ever holds an ID, email, token, cookie, query string, or other request value.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MonitoringMetricsIntegrationTest {

    private static final String PASSWORD = "River meadow lantern 42!";
    /** Values that must never appear in any metric tag. */
    private static final String SENTINEL = "zz-sentinel-9f3a";

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        }

        /** A route that always fails, to prove server errors are measured (registered as a member class). */
        @RestController
        static class FailingController {
            @GetMapping("/api/monitoring-test/failure/{id}")
            String fail() {
                throw new IllegalStateException("private failure detail " + SENTINEL);
            }
        }
    }

    @Autowired private MockMvc mvc;
    @Autowired private MeterRegistry registry;
    @Autowired private MutableClock clock;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JwtService jwtService;
    @Autowired private RefreshCookieService cookies;

    private User user;
    private String token;

    @BeforeEach
    void setUp() {
        clock.set(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        user = users.saveAndFlush(new User("Metric", "User", SENTINEL + "-" + UUID.randomUUID() + "@example.com",
                encoder.encode(PASSWORD)));
        token = jwtService.generateToken(user);
    }

    // ------------------------------------------------------------------ HTTP metrics

    @Test
    void recordsCountDurationAndRouteTemplatesNeverIdsOrQueryStrings() throws Exception {
        for (String path : List.of("/api/transactions/1", "/api/transactions/2", "/api/budgets/10",
                "/api/budgets/11", "/api/transactions?search=" + SENTINEL + "&page=0")) {
            mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
        }

        Set<String> uris = registry.find("http.server.requests").timers().stream()
                .map(timer -> timer.getId().getTag("uri")).collect(Collectors.toSet());
        assertThat(uris).contains("/api/transactions/{id}", "/api/budgets/{id}", "/api/transactions");
        assertThat(uris).noneMatch(uri -> uri.matches(".*/\\d+.*") || uri.contains("?") || uri.contains(SENTINEL));

        Timer byId = registry.find("http.server.requests").tag("uri", "/api/transactions/{id}").timer();
        assertThat(byId).isNotNull();
        assertThat(byId.count()).isGreaterThanOrEqualTo(2); // Both IDs, one series.
        assertThat(byId.totalTime(java.util.concurrent.TimeUnit.NANOSECONDS)).isPositive();
        assertThat(byId.getId().getTag("method")).isEqualTo("GET");
    }

    @Test
    void measuresClientAndServerErrorsByStatusAndOutcome() throws Exception {
        mvc.perform(get("/api/transactions/999999999").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
        assertThrows(ServletException.class, () -> mvc.perform(get("/api/monitoring-test/failure/7")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)));

        Timer notFound = registry.find("http.server.requests").tags("uri", "/api/transactions/{id}", "status", "404").timer();
        assertThat(notFound).isNotNull();
        assertThat(notFound.getId().getTag("outcome")).isEqualTo("CLIENT_ERROR");
        Timer serverError = registry.find("http.server.requests")
                .tags("uri", "/api/monitoring-test/failure/{id}", "status", "500").timer();
        assertThat(serverError).isNotNull();
        assertThat(serverError.getId().getTag("outcome")).isEqualTo("SERVER_ERROR");
        assertThat(serverError.getId().getTag("exception")).isEqualTo("IllegalStateException");
    }

    @Test
    void publicHealthTrafficIsMeasuredToo() throws Exception {
        mvc.perform(get("/actuator/health/readiness"));

        Set<String> healthRoutes = registry.find("http.server.requests").timers().stream()
                .map(timer -> timer.getId().getTag("uri"))
                .filter(uri -> uri.startsWith("/actuator/health")).collect(Collectors.toSet());
        assertThat(healthRoutes).isNotEmpty();
    }

    // ------------------------------------------------------------------ authentication failures

    @Test
    void countsAWrongPasswordAndAnUnknownEmailOnceEachAsInvalidCredentials() throws Exception {
        double before = failures("login", "invalid_credentials");

        login(user.getEmail(), "Wrong password " + SENTINEL);
        login(SENTINEL + "-nobody@example.com", PASSWORD);

        assertThat(failures("login", "invalid_credentials") - before).isEqualTo(2);
    }

    @Test
    void doesNotCountASuccessfulLoginRefreshOrLogout() throws Exception {
        double before = totalFailures();

        MvcResult login = login(user.getEmail(), PASSWORD);
        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        String refreshed = refreshCookie(refresh(cookieValue(login)));
        logout(refreshed);
        refresh(null); // No cookie at all: a signed-out visitor, not a failure.

        assertThat(totalFailures()).isEqualTo(before);
    }

    @Test
    void classifiesAccessTokenFailuresOnlyWhereARequestIsRefused() throws Exception {
        double missing = failures("access_token", "missing");
        double invalid = failures("access_token", "invalid");
        double expired = failures("access_token", "expired");

        mvc.perform(get("/api/transactions"));
        mvc.perform(get("/api/transactions").header(HttpHeaders.AUTHORIZATION, "Bearer " + SENTINEL));
        String soonExpired = jwtService.generateToken(user);
        clock.advance(Duration.ofMinutes(10));
        mvc.perform(get("/api/transactions").header(HttpHeaders.AUTHORIZATION, "Bearer " + soonExpired));
        // A bad token on a public endpoint refuses nothing, so it is not counted.
        mvc.perform(get("/api/health").header(HttpHeaders.AUTHORIZATION, "Bearer " + SENTINEL));

        assertThat(failures("access_token", "missing") - missing).isEqualTo(1);
        assertThat(failures("access_token", "invalid") - invalid).isEqualTo(1);
        assertThat(failures("access_token", "expired") - expired).isEqualTo(1);
    }

    @Test
    void classifiesRefreshTokenFailures() throws Exception {
        double malformed = failures("refresh_token", "malformed");
        double invalid = failures("refresh_token", "invalid");
        double reused = failures("refresh_token", "reused");
        double revoked = failures("refresh_token", "revoked");
        double expired = failures("refresh_token", "expired");

        refresh("not a token " + SENTINEL);                                // Unreadable.
        refresh("A".repeat(43));                                            // Well formed, unknown.
        String first = cookieValue(login(user.getEmail(), PASSWORD));
        refresh(first);                                                     // Rotates (success).
        refresh(first);                                                     // Old token again: reuse.
        refresh(first);                                                     // Its family is now revoked.
        String later = cookieValue(login(user.getEmail(), PASSWORD));
        clock.advance(Duration.ofDays(31));                                 // Past the 30-day lifetime.
        refresh(later);

        assertThat(failures("refresh_token", "malformed") - malformed).isEqualTo(1);
        assertThat(failures("refresh_token", "invalid") - invalid).isEqualTo(1);
        assertThat(failures("refresh_token", "reused") - reused).isEqualTo(1);
        assertThat(failures("refresh_token", "revoked") - revoked).isEqualTo(1);
        assertThat(failures("refresh_token", "expired") - expired).isEqualTo(1);
    }

    @Test
    void usesOnlyTheFixedFlowAndReasonValues() throws Exception {
        login(user.getEmail(), "Wrong password " + SENTINEL);
        mvc.perform(get("/api/transactions").header(HttpHeaders.AUTHORIZATION, "Bearer " + SENTINEL));
        refresh("not a token " + SENTINEL);

        for (Counter counter : registry.find(AuthenticationMetrics.METRIC).counters()) {
            assertThat(counter.getId().getTags()).extracting(Tag::getKey).containsExactlyInAnyOrder("flow", "reason");
            assertThat(counter.getId().getTag("flow")).isIn("login", "access_token", "refresh_token");
            assertThat(counter.getId().getTag("reason")).isIn("invalid_credentials", "missing", "invalid", "expired",
                    "malformed", "revoked", "reused");
        }
    }

    // ------------------------------------------------------------------ database pool and tag safety

    @Test
    void registersConnectionPoolMetricsTaggedOnlyByPool() {
        for (String name : List.of("hikaricp.connections.active", "hikaricp.connections.idle",
                "hikaricp.connections.pending", "hikaricp.connections.timeout", "hikaricp.connections.max",
                "hikaricp.connections.min")) {
            Meter meter = registry.find(name).meter();
            assertThat(meter).as(name).isNotNull();
            assertThat(meter.getId().getTags()).extracting(Tag::getKey).containsExactly("pool");
        }
    }

    @Test
    void noMetricTagHoldsARequestValue() throws Exception {
        login(user.getEmail(), "Wrong password " + SENTINEL);
        mvc.perform(get("/api/transactions/12345?search=" + SENTINEL).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .cookie(new Cookie(cookies.cookieName(), SENTINEL)));
        refresh(SENTINEL);

        for (Meter meter : registry.getMeters()) {
            for (Tag tag : meter.getId().getTags()) {
                assertThat(tag.getValue()).as("%s %s", meter.getId().getName(), tag.getKey())
                        .doesNotContain(SENTINEL, "@example.com", "12345", token, "Bearer");
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private double failures(String flow, String reason) {
        Counter counter = registry.find(AuthenticationMetrics.METRIC).tags("flow", flow, "reason", reason).counter();
        return counter == null ? 0 : counter.count();
    }

    private double totalFailures() {
        return registry.find(AuthenticationMetrics.METRIC).counters().stream().mapToDouble(Counter::count).sum();
    }

    private MvcResult login(String email, String password) throws Exception {
        return mvc.perform(protectedAuth(post("/api/auth/login")).contentType("application/json")
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")).andReturn();
    }

    private MvcResult refresh(String cookieValue) throws Exception {
        var request = protectedAuth(post("/api/auth/refresh"));
        if (cookieValue != null) request.cookie(new Cookie(cookies.cookieName(), cookieValue));
        return mvc.perform(request).andReturn();
    }

    private void logout(String cookieValue) throws Exception {
        mvc.perform(protectedAuth(post("/api/auth/logout")).cookie(new Cookie(cookies.cookieName(), cookieValue)));
    }

    private String cookieValue(MvcResult result) {
        return result.getResponse().getCookie(cookies.cookieName()).getValue();
    }

    private String refreshCookie(MvcResult result) {
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return cookieValue(result);
    }
}
