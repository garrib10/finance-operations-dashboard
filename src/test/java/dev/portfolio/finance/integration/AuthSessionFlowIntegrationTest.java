package dev.portfolio.finance.integration;

import static dev.portfolio.finance.support.AuthRequests.protectedAuth;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import dev.portfolio.finance.entity.RefreshSession;
import dev.portfolio.finance.entity.RefreshSessionRevocationReason;
import dev.portfolio.finance.entity.RefreshToken;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.repository.RefreshSessionRepository;
import dev.portfolio.finance.repository.RefreshTokenRepository;
import dev.portfolio.finance.repository.UserRepository;
import dev.portfolio.finance.security.RefreshCookieService;
import dev.portfolio.finance.security.RefreshTokenGenerator;
import dev.portfolio.finance.support.MutableClock;
import jakarta.servlet.http.Cookie;
import tools.jackson.databind.json.JsonMapper;

/** Login, refresh, reuse, and logout through the full security chain on H2. */
@SpringBootTest
// No MockMvc request/response dumps: they would print passwords and cookies on failure.
@AutoConfigureMockMvc(print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
@Import(AuthSessionFlowIntegrationTest.ClockConfig.class)
class AuthSessionFlowIntegrationTest {

    private static final String PASSWORD = "River meadow lantern 42!";
    private static final String EXPIRED_MESSAGE = "Your session has expired. Please sign in again.";

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        }
    }

    @Autowired private MockMvc mvc;
    @Autowired private MutableClock clock;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JsonMapper mapper;
    @Autowired private RefreshCookieService cookies;
    @Autowired private RefreshTokenGenerator generator;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private RefreshSessionRepository sessions;
    @MockitoSpyBean private RefreshTokenRepository tokens;
    @MockitoSpyBean private UserRepository users;

    private User user;
    private User other;

    @BeforeEach
    void setUp() {
        clock.set(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        String suffix = UUID.randomUUID().toString();
        user = users.saveAndFlush(new User("Flow", "User", "flow-" + suffix + "@example.com", encoder.encode(PASSWORD)));
        other = users.saveAndFlush(new User("Other", "User", "other-" + suffix + "@example.com", encoder.encode(PASSWORD)));
    }

    // ----- helpers -----------------------------------------------------------

    private MvcResult login(User account) throws Exception {
        return mvc.perform(protectedAuth(post("/api/auth/login")).contentType("application/json")
                        .content("{\"email\":\"" + account.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andReturn();
    }

    private static String setCookie(MvcResult result) {
        return result.getResponse().getHeader("Set-Cookie");
    }

    private String cookieValue(MvcResult result) {
        return result.getResponse().getCookie(cookies.cookieName()).getValue();
    }

    /** Exact header serialization is covered by the real-servlet suite; MockMvc re-serializes it. */
    private void assertIssuedCookie(MvcResult result, long maxAgeSeconds) {
        Cookie cookie = result.getResponse().getCookie(cookies.cookieName());
        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).hasSize(43);
        assertThat(cookie.getMaxAge()).isEqualTo((int) maxAgeSeconds);
        assertCookieScope(cookie);
    }

    private void assertClearedCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(cookies.cookieName());
        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.getMaxAge()).isZero();
        assertCookieScope(cookie);
    }

    private static void assertCookieScope(Cookie cookie) {
        assertThat(cookie.getPath()).isEqualTo("/api/auth");
        assertThat(cookie.getDomain()).isNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
    }

    private String loginToken(User account) throws Exception {
        MvcResult result = login(account);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return cookieValue(result);
    }

    private MockHttpServletRequestBuilder withCookie(MockHttpServletRequestBuilder builder, String value) {
        return builder.cookie(new Cookie(cookies.cookieName(), value));
    }

    private MvcResult refresh(String value) throws Exception {
        return mvc.perform(withCookie(protectedAuth(post("/api/auth/refresh")), value)).andReturn();
    }

    private MvcResult logout(String value) throws Exception {
        return mvc.perform(withCookie(protectedAuth(post("/api/auth/logout")), value)).andReturn();
    }

    private String accessToken(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString()).get("accessToken").stringValue();
    }

    private RefreshToken stored(String raw) {
        return tokens.findByTokenHash(generator.hashPresentedToken(raw)).orElseThrow();
    }

    private RefreshSession sessionOf(String raw) {
        return sessions.findById(stored(raw).getSession().getId()).orElseThrow();
    }

    private List<RefreshSession> sessionsOf(User account) {
        return sessions.findByUser_IdOrderByCreatedAtAsc(account.getId());
    }

    private long tokenRows() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens", Long.class);
    }

    private void assertTerminal(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        String body = result.getResponse().getContentAsString();
        assertThat(mapper.readTree(body).get("code").stringValue()).isEqualTo("SESSION_EXPIRED");
        assertThat(mapper.readTree(body).get("message").stringValue()).isEqualTo(EXPIRED_MESSAGE);
        assertThat(body).doesNotContainIgnoringCase("reuse").doesNotContain("revoked", "unknown", "sessionId");
        assertClearedCookie(result);
    }

    // ----- login -------------------------------------------------------------

    @Test
    void loginIssuesIndependentFamilyStoringOnlyTheHash() throws Exception {
        MvcResult result = login(user);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String body = result.getResponse().getContentAsString();
        var json = mapper.readTree(body);
        assertThat(json.get("tokenType").stringValue()).isEqualTo("Bearer");
        assertThat(json.get("expiresIn").asLong()).isEqualTo(300);
        assertThat(json.propertyNames()).containsExactlyInAnyOrder("accessToken", "tokenType", "expiresIn");

        String raw = cookieValue(result);
        assertThat(raw).hasSize(43);
        assertThat(body).doesNotContain(raw);
        assertIssuedCookie(result, Duration.ofDays(30).toSeconds());

        List<RefreshSession> families = sessionsOf(user);
        assertThat(families).hasSize(1);
        RefreshSession family = families.getFirst();
        assertThat(family.getCreatedAt()).isEqualTo(clock.instant());
        assertThat(family.getExpiresAt()).isEqualTo(clock.instant().plus(Duration.ofDays(30)));
        assertThat(family.isRevoked()).isFalse();
        assertThat(body).doesNotContain(family.getId());

        List<RefreshToken> history = tokens.findBySession_IdOrderByIdAsc(family.getId());
        assertThat(history).hasSize(1);
        byte[] storedHash = history.getFirst().getTokenHash();
        assertThat(storedHash).isEqualTo(generator.hashPresentedToken(raw))
                .isNotEqualTo(raw.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        List<String> textColumns = jdbc.queryForList(
                "SELECT CONCAT(CAST(id AS VARCHAR), session_id, CAST(created_at AS VARCHAR), "
                        + "COALESCE(CAST(consumed_at AS VARCHAR), '')) FROM refresh_tokens", String.class);
        assertThat(textColumns).noneMatch(row -> row.contains(raw));

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + accessToken(result)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(user.getEmail()));
    }

    @Test
    void multipleLoginsCreateIndependentFamilies() throws Exception {
        String laptop = loginToken(user);
        String phone = loginToken(user);

        assertThat(laptop).isNotEqualTo(phone);
        assertThat(sessionsOf(user)).hasSize(2);
        assertThat(sessionOf(laptop).getId()).isNotEqualTo(sessionOf(phone).getId());
    }

    @Test
    void failedLoginCreatesNoSessionAndNoCookie() throws Exception {
        long before = tokenRows();

        MvcResult wrongPassword = mvc.perform(protectedAuth(post("/api/auth/login")).contentType("application/json")
                .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"wrong password value\"}")).andReturn();
        MvcResult unknownEmail = mvc.perform(protectedAuth(post("/api/auth/login")).contentType("application/json")
                .content("{\"email\":\"nobody@example.com\",\"password\":\"" + PASSWORD + "\"}")).andReturn();

        for (MvcResult result : List.of(wrongPassword, unknownEmail)) {
            assertThat(result.getResponse().getStatus()).isEqualTo(401);
            assertThat(result.getResponse().getContentAsString()).contains("Invalid email or password");
            assertThat(setCookie(result)).isNull();
        }
        assertThat(sessionsOf(user)).isEmpty();
        assertThat(tokenRows()).isEqualTo(before);
    }

    @Test
    void loginPersistenceFailureReturns503WithoutCookieOrPartialSession() throws Exception {
        doThrow(new DataAccessResourceFailureException("db down: token_hash=secret"))
                .when(tokens).save(any(RefreshToken.class));

        MvcResult result = login(user);

        assertThat(result.getResponse().getStatus()).isEqualTo(503);
        assertThat(result.getResponse().getContentAsString())
                .contains("\"code\":\"SESSION_UNAVAILABLE\"")
                .doesNotContain("accessToken", "db down", "token_hash");
        assertThat(setCookie(result)).isNull();
        assertThat(sessionsOf(user)).isEmpty();
    }

    @Test
    void loginWithoutRequestProtectionCreatesNothing() throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(setCookie(result)).isNull();
        assertThat(sessionsOf(user)).isEmpty();
    }

    @Test
    void registrationStillDoesNotSignIn() throws Exception {
        String email = "register-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mvc.perform(post("/api/auth/register").contentType("application/json")
                .content("{\"firstName\":\"New\",\"lastName\":\"User\",\"email\":\"" + email
                        + "\",\"password\":\"" + PASSWORD + "\"}")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(setCookie(result)).isNull();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("accessToken");
        assertThat(sessionsOf(users.findByEmail(email).orElseThrow())).isEmpty();
    }

    // ----- refresh -----------------------------------------------------------

    @Test
    void refreshRotatesWithinFamilyWithoutExtendingExpiration() throws Exception {
        MvcResult loggedIn = login(user);
        String first = cookieValue(loggedIn);
        String oldAccess = accessToken(loggedIn);
        RefreshSession family = sessionOf(first);
        clock.advance(Duration.ofMinutes(10));

        // No bearer token and an empty body.
        MvcResult result = refresh(first);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String second = cookieValue(result);
        assertThat(second).isNotEqualTo(first).hasSize(43);
        assertThat(result.getResponse().getContentAsString()).doesNotContain(first, second);
        assertIssuedCookie(result, Duration.ofDays(30).minusMinutes(10).toSeconds());

        RefreshToken consumed = stored(first);
        assertThat(consumed.getConsumedAt()).isEqualTo(clock.instant());
        RefreshToken replacement = stored(second);
        assertThat(replacement.getSession().getId()).isEqualTo(family.getId());
        assertThat(replacement.getConsumedAt()).isNull();
        assertThat(replacement.getCreatedAt()).isEqualTo(clock.instant());
        assertThat(sessionOf(second).getExpiresAt()).isEqualTo(family.getExpiresAt());
        assertThat(tokens.findBySession_IdOrderByIdAsc(family.getId())).hasSize(2);

        // The old access token has expired; the new one works.
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + oldAccess))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ACCESS_TOKEN_EXPIRED"));
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + accessToken(result)))
                .andExpect(status().isOk());
    }

    @Test
    void reuseRevokesWholeFamilyCommitsAndLeavesOtherDevicesActive(CapturedOutput output) throws Exception {
        String laptop1 = loginToken(user);
        String phone1 = loginToken(user);
        String laptop2 = cookieValue(refresh(laptop1));

        MvcResult reused = refresh(laptop1);

        assertTerminal(reused);
        RefreshSession laptopFamily = sessionOf(laptop2);
        assertThat(laptopFamily.getRevocationReason()).isEqualTo(RefreshSessionRevocationReason.REUSE_DETECTED);
        assertThat(laptopFamily.getRevokedAt()).isEqualTo(clock.instant());
        assertTerminal(refresh(laptop2));
        assertThat(stored(laptop2).getConsumedAt()).isNull();

        assertThat(sessionOf(phone1).isRevoked()).isFalse();
        assertThat(refresh(phone1).getResponse().getStatus()).isEqualTo(200);

        String hex = HexFormat.of().formatHex(generator.hashPresentedToken(laptop1));
        assertThat(output.getAll()).contains("auth.refresh.reuse_detected")
                .doesNotContain(laptop1, laptop2, phone1, hex, "Set-Cookie", "Cookie:");
    }

    @Test
    void terminalRefreshFailuresAreIndistinguishableAndClearTheCookie() throws Exception {
        String active = loginToken(other);
        String unknown = generator.generate().rawToken();
        String revoked = loginToken(user);
        logout(revoked);
        String expired = loginToken(user);
        clock.advance(Duration.ofDays(30));
        long before = tokenRows();

        List<MvcResult> results = new java.util.ArrayList<>(List.of(
                mvc.perform(protectedAuth(post("/api/auth/refresh"))).andReturn(),
                refresh(""),
                refresh(unknown.substring(0, 42) + "="),
                refresh("not a token"),
                refresh(unknown),
                refresh(revoked),
                refresh(expired)));
        results.add(mvc.perform(protectedAuth(post("/api/auth/refresh"))
                .cookie(new Cookie(cookies.cookieName(), active), new Cookie(cookies.cookieName(), unknown)))
                .andReturn());

        for (MvcResult result : results) {
            assertTerminal(result);
        }
        assertThat(tokenRows()).isEqualTo(before);
        assertThat(sessionOf(active).isRevoked()).isFalse();
        assertThat(stored(active).getConsumedAt()).isNull();
        assertThat(sessionOf(expired).isRevoked()).isFalse();
        assertThat(sessionOf(revoked).getRevocationReason()).isEqualTo(RefreshSessionRevocationReason.LOGOUT);
    }

    @Test
    void refreshAcceptsTokensOnlyFromTheCookie() throws Exception {
        String raw = loginToken(user);

        List<MvcResult> results = List.of(
                mvc.perform(protectedAuth(post("/api/auth/refresh")).contentType("application/json")
                        .content("{\"refreshToken\":\"" + raw + "\"}")).andReturn(),
                mvc.perform(protectedAuth(post("/api/auth/refresh")).param("refreshToken", raw)).andReturn(),
                mvc.perform(protectedAuth(post("/api/auth/refresh")).header("Authorization", "Bearer " + raw))
                        .andReturn(),
                mvc.perform(protectedAuth(post("/api/auth/refresh")).header("X-Refresh-Token", raw)).andReturn());

        for (MvcResult result : results) {
            assertTerminal(result);
        }
        assertThat(stored(raw).getConsumedAt()).isNull();
    }

    @Test
    void refreshProtectionFailureChangesNothingAndKeepsCookie() throws Exception {
        String raw = loginToken(user);

        MvcResult noHeader = mvc.perform(withCookie(post("/api/auth/refresh")
                .header("Origin", dev.portfolio.finance.support.AuthRequests.ORIGIN), raw)).andReturn();
        MvcResult badOrigin = mvc.perform(withCookie(post("/api/auth/refresh")
                .header("Origin", "https://evil.example").header("X-FinTrack-CSRF", "1"), raw)).andReturn();

        for (MvcResult result : List.of(noHeader, badOrigin)) {
            assertThat(result.getResponse().getStatus()).isEqualTo(403);
            assertThat(setCookie(result)).isNull();
        }
        assertThat(stored(raw).getConsumedAt()).isNull();
        assertThat(refresh(raw).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void refreshPersistenceFailureReturns503WithoutRotatingOrClearing() throws Exception {
        String raw = loginToken(user);
        doThrow(new DataAccessResourceFailureException("lock wait timeout"))
                .when(tokens).findByTokenHashForUpdate(any());

        MvcResult result = refresh(raw);

        assertThat(result.getResponse().getStatus()).isEqualTo(503);
        assertThat(result.getResponse().getContentAsString())
                .contains("\"code\":\"SESSION_UNAVAILABLE\"").doesNotContain("lock wait");
        assertThat(setCookie(result)).isNull();
        assertThat(stored(raw).getConsumedAt()).isNull();
        assertThat(sessionOf(raw).isRevoked()).isFalse();
    }

    @Test
    void failureWhileSavingReplacementRollsBackConsumption() throws Exception {
        String raw = loginToken(user);
        doThrow(new DataAccessResourceFailureException("insert failed"))
                .when(tokens).save(any(RefreshToken.class));

        MvcResult result = refresh(raw);

        assertThat(result.getResponse().getStatus()).isEqualTo(503);
        assertThat(setCookie(result)).isNull();
        assertThat(stored(raw).getConsumedAt()).isNull();
        assertThat(tokens.findBySession_IdOrderByIdAsc(sessionOf(raw).getId())).hasSize(1);
    }

    // ----- logout ------------------------------------------------------------

    @Test
    void logoutRevokesOnlyTheCurrentFamilyAndIsIdempotent() throws Exception {
        String laptop = loginToken(user);
        String phone = loginToken(user);

        MvcResult first = logout(laptop);

        assertThat(first.getResponse().getStatus()).isEqualTo(204);
        assertThat(first.getResponse().getContentAsString()).isEmpty();
        assertClearedCookie(first);
        assertThat(sessionOf(laptop).getRevocationReason()).isEqualTo(RefreshSessionRevocationReason.LOGOUT);
        assertThat(sessionOf(phone).isRevoked()).isFalse();
        Instant revokedAt = sessionOf(laptop).getRevokedAt();

        clock.advance(Duration.ofMinutes(1));
        MvcResult repeated = logout(laptop);
        assertThat(repeated.getResponse().getStatus()).isEqualTo(204);
        assertClearedCookie(repeated);
        assertThat(sessionOf(laptop).getRevokedAt()).isEqualTo(revokedAt);
        assertTerminal(refresh(laptop));
        assertThat(refresh(phone).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void logoutNoOpCasesStillReturn204AndClear() throws Exception {
        String active = loginToken(other);
        String expired = loginToken(user);
        clock.advance(Duration.ofDays(31));
        String unrelated = loginToken(other);

        List<MvcResult> results = List.of(
                mvc.perform(protectedAuth(post("/api/auth/logout"))).andReturn(),
                logout(""),
                logout("malformed=="),
                logout(generator.generate().rawToken()),
                logout(expired),
                mvc.perform(protectedAuth(post("/api/auth/logout"))
                        .cookie(new Cookie(cookies.cookieName(), unrelated), new Cookie(cookies.cookieName(), active)))
                        .andReturn());

        for (MvcResult result : results) {
            assertThat(result.getResponse().getStatus()).isEqualTo(204);
            assertClearedCookie(result);
        }
        assertThat(sessionOf(expired).isRevoked()).isFalse();
        assertThat(sessionOf(unrelated).isRevoked()).isFalse();
    }

    @Test
    void logoutWithConsumedTokenRevokesFamilyAsReuse() throws Exception {
        String first = loginToken(user);
        String second = cookieValue(refresh(first));

        assertThat(logout(first).getResponse().getStatus()).isEqualTo(204);

        assertThat(sessionOf(second).getRevocationReason()).isEqualTo(RefreshSessionRevocationReason.REUSE_DETECTED);
        assertTerminal(refresh(second));
    }

    @Test
    void logoutProtectionFailureDoesNotRevokeOrClear() throws Exception {
        String raw = loginToken(user);

        MvcResult result = mvc.perform(withCookie(post("/api/auth/logout").header("X-FinTrack-CSRF", "1"), raw))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(setCookie(result)).isNull();
        assertThat(sessionOf(raw).isRevoked()).isFalse();
    }

    @Test
    void logoutPersistenceFailureReturns503AndKeepsCookie() throws Exception {
        String raw = loginToken(user);
        doThrow(new DataAccessResourceFailureException("connection reset"))
                .when(users).findByIdForUpdate(any());

        MvcResult result = logout(raw);

        assertThat(result.getResponse().getStatus()).isEqualTo(503);
        assertThat(setCookie(result)).isNull();
        assertThat(result.getResponse().getContentAsString()).contains("SESSION_UNAVAILABLE");
        assertThat(sessionOf(raw).isRevoked()).isFalse();
    }

    // ----- boundaries --------------------------------------------------------

    @Test
    void refreshCookieNeverAuthenticatesBusinessEndpoints() throws Exception {
        String raw = loginToken(user);

        for (MockHttpServletRequestBuilder request : List.of(
                get("/api/auth/me"), get("/api/transactions"), get("/api/budgets"), get("/api/categories"),
                get("/api/dashboard"), put("/api/account/profile").contentType("application/json").content("{}"),
                post("/api/account/password").contentType("application/json").content("{}"))) {
            mvc.perform(protectedAuth(withCookie(request, raw)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                    .andExpect(header().doesNotExist("Set-Cookie"));
        }
        assertThat(stored(raw).getConsumedAt()).isNull();
    }

    @Test
    void healthStaysPublic() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk());
    }

    @Test
    void sessionLifecycleLogsFixedEventsOnly(CapturedOutput output) throws Exception {
        MvcResult loggedIn = login(user);
        String raw = cookieValue(loggedIn);
        String access = accessToken(loggedIn);
        String rotated = cookieValue(refresh(raw));
        refresh("bad-token-probe-value");
        logout(rotated);
        mvc.perform(protectedAuth(post("/api/auth/logout"))).andReturn();

        String hex = HexFormat.of().formatHex(generator.hashPresentedToken(raw));
        assertThat(output.getAll())
                .contains("auth.login.succeeded", "auth.session.started", "auth.refresh.rotated",
                        "auth.refresh.rejected category=malformed", "auth.logout.revoked",
                        "auth.logout.noop category=cookie_absent")
                .doesNotContain(raw, rotated, access, hex, "bad-token-probe-value", PASSWORD,
                        "Set-Cookie", cookies.cookieName() + "=");
    }
}
