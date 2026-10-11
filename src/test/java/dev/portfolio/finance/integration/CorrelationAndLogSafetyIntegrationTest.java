package dev.portfolio.finance.integration;

import static dev.portfolio.finance.support.AuthRequests.protectedAuth;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.monitoring.CorrelationIdFilter;
import dev.portfolio.finance.repository.UserRepository;
import dev.portfolio.finance.security.JwtService;
import dev.portfolio.finance.security.RefreshCookieService;

/**
 * Issue #20, Phase 2, through the real filter chain: every response carries one correlation
 * ID, the logging context never outlives its request, and none of the user's secrets or
 * financial data reach the logs, even when requests fail.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class CorrelationAndLogSafetyIntegrationTest {

    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    // Distinctive values standing in for each kind of private data.
    private static final String PASSWORD = "Sentinel-Password-7x41!";
    private static final String WRONG_PASSWORD = "Sentinel-Wrong-Pass-3k9";
    private static final String EMAIL_LOCAL = "sentinel-mail-5q2";
    private static final String AUTH_HEADER = "sentinelAuthHeader8v1";
    private static final String COOKIE = "sentinelCookieValue6m3";
    private static final String DESCRIPTION = "Sentinel description 4p8";
    private static final String AMOUNT = "4321.87";
    private static final String CATEGORY = "Sentinel Category 2w7";
    private static final String DISPLAY_NAME = "Sentinel Display 9h6";
    private static final String FAILURE_DETAIL = "sentinel-failure-detail-1z5";

    @TestConfiguration
    static class Config {
        /** Fails with a private detail in its message, which must never be logged. */
        @RestController
        static class FailingController {
            @GetMapping("/api/correlation-test/failure")
            String fail() {
                throw new IllegalStateException("jdbc:mysql://db.internal user=root " + FAILURE_DETAIL);
            }
        }
    }

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JwtService jwtService;
    @Autowired private RefreshCookieService cookies;

    private User user;
    private String token;

    @BeforeEach
    void setUp() {
        user = users.saveAndFlush(new User("Log", "Safety", EMAIL_LOCAL + "-" + UUID.randomUUID() + "@example.com",
                encoder.encode(PASSWORD)));
        token = jwtService.generateToken(user);
    }

    // ------------------------------------------------------------------ correlation IDs

    @Test
    void generatesAnIdWhenNoneIsSentAndKeepsASafeOne() throws Exception {
        String generated = mvc.perform(get("/api/health")).andReturn().getResponse().getHeader(CorrelationIdFilter.HEADER);
        String kept = mvc.perform(get("/api/health").header(CorrelationIdFilter.HEADER, "support-ticket-42"))
                .andReturn().getResponse().getHeader(CorrelationIdFilter.HEADER);

        assertThat(generated).matches(UUID_PATTERN);
        assertThat(kept).isEqualTo("support-ticket-42");
    }

    @Test
    void replacesAnUnsafeIdWithoutRejectingTheRequest() throws Exception {
        MvcResult result = mvc.perform(get("/api/health").header(CorrelationIdFilter.HEADER, "bad id;\r\nInjected: yes"))
                .andExpect(status().isOk()).andReturn();

        assertThat(result.getResponse().getHeader(CorrelationIdFilter.HEADER)).matches(UUID_PATTERN);
        assertThat(result.getResponse().getHeader("Injected")).isNull();
    }

    @Test
    void errorResponsesCarryTheIdToo() throws Exception {
        MvcResult unauthorized = mvc.perform(get("/api/transactions").header(CorrelationIdFilter.HEADER, "trace-401"))
                .andExpect(status().isUnauthorized()).andReturn();
        MvcResult notFound = mvc.perform(get("/api/transactions/999999999").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound()).andReturn();

        assertThat(unauthorized.getResponse().getHeader(CorrelationIdFilter.HEADER)).isEqualTo("trace-401");
        assertThat(notFound.getResponse().getHeader(CorrelationIdFilter.HEADER)).matches(UUID_PATTERN);
    }

    @Test
    void aCorrelationIdNeverAuthenticatesAnything() throws Exception {
        mvc.perform(get("/api/transactions").header(CorrelationIdFilter.HEADER, "admin"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/metrics").header(CorrelationIdFilter.HEADER, "operator"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sequentialRequestsGetTheirOwnIdsAndLeaveNoContextBehind() throws Exception {
        String first = mvc.perform(get("/api/health")).andReturn().getResponse().getHeader(CorrelationIdFilter.HEADER);
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
        String second = mvc.perform(get("/api/health")).andReturn().getResponse().getHeader(CorrelationIdFilter.HEADER);

        assertThat(second).isNotEqualTo(first);
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void anUnhandledFailureIsLoggedOnceWithItsIdAndClassButNotItsMessage(CapturedOutput output) {
        assertThrows(ServletException.class, () -> mvc.perform(get("/api/correlation-test/failure")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(CorrelationIdFilter.HEADER, "trace-failure-7")));

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
        String failureLine = output.getAll().lines().filter(line -> line.contains("http.request.failed"))
                .reduce((a, b) -> b).orElseThrow();
        assertThat(failureLine).contains("trace-failure-7", "IllegalStateException", "/api/correlation-test/failure",
                "500");
        assertThat(failureLine).doesNotContain(FAILURE_DETAIL, "jdbc:", "user=root");
    }

    // ------------------------------------------------------------------ nothing private in the logs

    @Test
    void keepsCredentialsTokensAndFinancialDataOutOfTheLogs(CapturedOutput output) throws Exception {
        // Failed and successful sign-in, refresh with a bad cookie, and a bad bearer token.
        mvc.perform(protectedAuth(post("/api/auth/login")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"" + WRONG_PASSWORD + "\"}"));
        MvcResult login = mvc.perform(protectedAuth(post("/api/auth/login")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn();
        String issuedRefresh = login.getResponse().getCookie(cookies.cookieName()).getValue();
        mvc.perform(protectedAuth(post("/api/auth/refresh")).cookie(new Cookie(cookies.cookieName(), COOKIE)));
        mvc.perform(get("/api/transactions").header(HttpHeaders.AUTHORIZATION, "Bearer " + AUTH_HEADER));

        // Financial and profile data, including a request that fails validation.
        mvc.perform(post("/api/transactions").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"newCategory": {"name": "%s", "iconKey": "tag"}, "type": "EXPENSE", "amount": %s,
                         "description": "%s", "transactionDate": "%s"}
                        """.formatted(CATEGORY, AMOUNT, DESCRIPTION, LocalDate.now())))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/transactions").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\": \"EXPENSE\", \"amount\": -" + AMOUNT + ", \"description\": \"" + DESCRIPTION + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/account/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\": \"Log\", \"lastName\": \"Safety\", \"displayName\": \"" + DISPLAY_NAME + "\"}"));
        assertThrows(ServletException.class, () -> mvc.perform(get("/api/correlation-test/failure")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)));

        String logs = output.getAll();
        List<String> leaked = List.of(PASSWORD, WRONG_PASSWORD, EMAIL_LOCAL, AUTH_HEADER, COOKIE, token, issuedRefresh,
                DESCRIPTION, AMOUNT, CATEGORY, DISPLAY_NAME).stream().filter(logs::contains).toList();
        // Report which kind leaked by index, without printing the value itself.
        assertThat(leaked).as("private values found in the logs: %d", leaked.size()).isEmpty();
        assertThat(logs).contains("http.request.failed"); // Logging was captured at all.
    }
}
