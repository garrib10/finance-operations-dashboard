package dev.portfolio.finance.mysql;

import static dev.portfolio.finance.support.AuthRequests.protectedAuth;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import dev.portfolio.finance.entity.RefreshToken;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.repository.RefreshSessionRepository;
import dev.portfolio.finance.repository.RefreshTokenRepository;
import dev.portfolio.finance.repository.UserRepository;
import dev.portfolio.finance.security.IssuedRefreshToken;
import dev.portfolio.finance.security.RefreshCookieService;
import dev.portfolio.finance.security.RefreshTokenGenerator;
import jakarta.servlet.http.Cookie;
import tools.jackson.databind.json.JsonMapper;

/** HTTP-level session lifecycle on real MySQL, including rollback and log redaction. */
@ExtendWith(OutputCaptureExtension.class)
class AuthSessionMySqlIT extends MySqlIntegrationTestBase {

    private static final String OLD = "River meadow lantern 42!";
    private static final String NEXT = "Another quiet forest 73!";

    @Autowired private MockMvc mvc;
    @Autowired private JsonMapper mapper;
    @Autowired private PasswordEncoder encoder;
    @Autowired private RefreshCookieService cookies;
    @Autowired private UserRepository users;
    @MockitoSpyBean private RefreshTokenRepository tokens;
    @MockitoSpyBean private RefreshSessionRepository sessions;
    @MockitoSpyBean private RefreshTokenGenerator generator;

    private User user;
    private User other;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString();
        user = users.saveAndFlush(new User("My", "Sql", "mysql-" + suffix + "@example.com", encoder.encode(OLD)));
        other = users.saveAndFlush(new User("Other", "Sql", "other-" + suffix + "@example.com", encoder.encode(OLD)));
    }

    private MvcResult login(User account, String password) throws Exception {
        return mvc.perform(protectedAuth(post("/api/auth/login")).contentType("application/json")
                .content(mapper.writeValueAsString(new dev.portfolio.finance.dto.auth.LoginRequest(
                        account.getEmail(), password)))).andReturn();
    }

    private String cookieOf(MvcResult result) {
        return result.getResponse().getCookie(cookies.cookieName()).getValue();
    }

    private String accessOf(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString()).get("accessToken").stringValue();
    }

    private MvcResult refresh(String raw) throws Exception {
        return mvc.perform(protectedAuth(post("/api/auth/refresh")).cookie(new Cookie(cookies.cookieName(), raw)))
                .andReturn();
    }

    private MvcResult changePassword(String access, String current, String next) throws Exception {
        return mvc.perform(post("/api/account/password").header("Authorization", "Bearer " + access)
                .contentType("application/json")
                .content(mapper.writeValueAsString(new dev.portfolio.finance.dto.account.ChangePasswordRequest(
                        current, next)))).andReturn();
    }

    private String reasonOf(String raw) {
        return jdbc.queryForObject("SELECT COALESCE(s.revocation_reason, 'ACTIVE') FROM refresh_sessions s "
                + "JOIN refresh_tokens t ON t.session_id = s.id WHERE t.token_hash = ?", String.class,
                (Object) generator.hashPresentedToken(raw));
    }

    private List<String> reasonsFor(User account) {
        return jdbc.queryForList("SELECT COALESCE(revocation_reason, 'ACTIVE') FROM refresh_sessions "
                + "WHERE user_id = ? ORDER BY created_at, id", String.class, account.getId());
    }

    private long tokenRowsFor(User account) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens t JOIN refresh_sessions s "
                + "ON s.id = t.session_id WHERE s.user_id = ?", Long.class, account.getId());
    }

    @Test
    void reuseRevocationCommitsBeforeThe401() throws Exception {
        String first = cookieOf(login(user, OLD));
        String second = cookieOf(refresh(first));

        MvcResult reused = refresh(first);

        assertThat(reused.getResponse().getStatus()).isEqualTo(401);
        assertThat(reused.getResponse().getContentAsString()).contains("SESSION_EXPIRED")
                .doesNotContainIgnoringCase("reuse");
        assertThat(reused.getResponse().getCookie(cookies.cookieName()).getMaxAge()).isZero();
        // Read through a fresh pooled connection: the revocation is committed.
        assertThat(reasonOf(second)).isEqualTo("REUSE_DETECTED");
        assertThat(refresh(second).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void passwordChangeRevokesEveryFamilyAndClearsCookieAfterCommit() throws Exception {
        MvcResult laptop = login(user, OLD);
        String phone = cookieOf(login(user, OLD));
        String alreadyLoggedOut = cookieOf(login(user, OLD));
        mvc.perform(protectedAuth(post("/api/auth/logout")).cookie(new Cookie(cookies.cookieName(), alreadyLoggedOut)));
        String otherUser = cookieOf(login(other, OLD));
        long history = tokenRowsFor(user);
        clock.advance(java.time.Duration.ofMinutes(2));
        Instant changedAt = clock.instant();

        MvcResult changed = changePassword(accessOf(laptop), OLD, NEXT);

        assertThat(changed.getResponse().getStatus()).isEqualTo(204);
        assertThat(changed.getResponse().getContentAsString()).isEmpty();
        Cookie cleared = changed.getResponse().getCookie(cookies.cookieName());
        assertThat(cleared.getValue()).isEmpty();
        assertThat(cleared.getMaxAge()).isZero();
        assertThat(cleared.getPath()).isEqualTo("/api/auth");
        assertThat(reasonsFor(user)).containsExactlyInAnyOrder("PASSWORD_CHANGE", "PASSWORD_CHANGE", "LOGOUT");
        assertThat(jdbc.queryForList("SELECT revoked_at FROM refresh_sessions WHERE user_id = ? "
                + "AND revocation_reason = 'PASSWORD_CHANGE'", java.sql.Timestamp.class, user.getId()))
                .allSatisfy(at -> assertThat(at.toLocalDateTime())
                        .isEqualTo(java.time.LocalDateTime.ofInstant(changedAt, java.time.ZoneOffset.UTC)));
        assertThat(tokenRowsFor(user)).isEqualTo(history);
        assertThat(reasonOf(otherUser)).isEqualTo("ACTIVE");

        assertThat(refresh(cookieOf(laptop)).getResponse().getStatus()).isEqualTo(401);
        assertThat(refresh(phone).getResponse().getStatus()).isEqualTo(401);
        // Stateless access JWTs keep working until they expire (at most five minutes).
        assertThat(mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + accessOf(laptop)))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(login(user, OLD).getResponse().getStatus()).isEqualTo(401);
        MvcResult fresh = login(user, NEXT);
        assertThat(fresh.getResponse().getStatus()).isEqualTo(200);
        assertThat(reasonOf(cookieOf(fresh))).isEqualTo("ACTIVE");
    }

    @Test
    void rejectedPasswordChangesTouchNothing() throws Exception {
        MvcResult session = login(user, OLD);
        String hash = users.findById(user.getId()).orElseThrow().getPasswordHash();

        assertThat(changePassword(accessOf(session), "wrong current password", NEXT).getResponse().getStatus())
                .isEqualTo(400);
        assertThat(changePassword(accessOf(session), OLD, "short").getResponse().getStatus()).isEqualTo(400);
        assertThat(changePassword(accessOf(session), OLD, OLD).getResponse().getStatus()).isEqualTo(400);

        assertThat(users.findById(user.getId()).orElseThrow().getPasswordHash()).isEqualTo(hash);
        assertThat(reasonOf(cookieOf(session))).isEqualTo("ACTIVE");
    }

    @Test
    void failureDuringRevocationRollsBackPasswordAndKeepsCookie() throws Exception {
        MvcResult session = login(user, OLD);
        doThrow(new DataAccessResourceFailureException("simulated lock wait timeout"))
                .when(sessions).findUnrevokedByUserIdForUpdate(anyLong());

        MvcResult failed = changePassword(accessOf(session), OLD, NEXT);

        assertThat(failed.getResponse().getStatus()).isEqualTo(500);
        assertThat(failed.getResponse().getHeader("Set-Cookie")).isNull();
        assertThat(failed.getResponse().getContentAsString()).doesNotContain("simulated", "lock wait");
        assertThat(encoder.matches(OLD, users.findById(user.getId()).orElseThrow().getPasswordHash())).isTrue();
        assertThat(reasonOf(cookieOf(session))).isEqualTo("ACTIVE");
    }

    @Test
    void failureDuringRotationLeavesPreviousStateAndIssuesNoCookie() throws Exception {
        String token = cookieOf(login(user, OLD));
        long history = tokenRowsFor(user);
        doThrow(new DataAccessResourceFailureException("simulated insert failure"))
                .when(tokens).save(any(RefreshToken.class));

        MvcResult failed = refresh(token);

        assertThat(failed.getResponse().getStatus()).isEqualTo(503);
        assertThat(failed.getResponse().getHeader("Set-Cookie")).isNull();
        assertThat(tokenRowsFor(user)).isEqualTo(history);
        assertThat(jdbc.queryForObject("SELECT consumed_at FROM refresh_tokens WHERE token_hash = ?",
                java.sql.Timestamp.class, (Object) generator.hashPresentedToken(token))).isNull();
        assertThat(reasonOf(token)).isEqualTo("ACTIVE");
    }

    @Test
    void duplicateHashFailsSafelyWithoutLoggingIt(CapturedOutput output) throws Exception {
        AtomicReference<IssuedRefreshToken> first = new AtomicReference<>();
        doAnswer(invocation -> {
            IssuedRefreshToken issued = first.get();
            if (issued == null) {
                issued = (IssuedRefreshToken) invocation.callRealMethod();
                first.set(issued);
            }
            return issued;
        }).when(generator).generate();

        MvcResult ok = login(user, OLD);
        MvcResult duplicate = login(other, OLD);

        assertThat(ok.getResponse().getStatus()).isEqualTo(200);
        assertThat(duplicate.getResponse().getStatus()).isEqualTo(503);
        assertThat(duplicate.getResponse().getHeader("Set-Cookie")).isNull();
        assertThat(duplicate.getResponse().getContentAsString()).contains("SESSION_UNAVAILABLE")
                .doesNotContain("Duplicate", "token_hash");
        assertThat(reasonsFor(other)).isEmpty();
        String hex = HexFormat.of().formatHex(first.get().tokenHash());
        assertThat(output.getAll())
                .contains("auth.session.persistence_failed operation=login")
                .doesNotContain(first.get().rawToken(), hex, hex.toUpperCase(), "Duplicate entry",
                        "uk_refresh_tokens_token_hash");
    }

    @Test
    void profilePreferenceAndOtherUsersChangesDoNotRevoke() throws Exception {
        MvcResult session = login(user, OLD);
        String access = accessOf(session);

        assertThat(mvc.perform(put("/api/account/profile").header("Authorization", "Bearer " + access)
                .contentType("application/json").content("{\"firstName\":\"New\",\"lastName\":\"Name\","
                        + "\"displayName\":\"New Name\"}")).andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(mvc.perform(put("/api/account/preferences").header("Authorization", "Bearer " + access)
                .contentType("application/json").content("{\"dateFormat\":\"ISO\",\"transactionPageSize\":25}"))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
        MvcResult otherSession = login(other, OLD);
        assertThat(changePassword(accessOf(otherSession), OLD, NEXT).getResponse().getStatus()).isEqualTo(204);

        assertThat(reasonOf(cookieOf(session))).isEqualTo("ACTIVE");
        assertThat(refresh(cookieOf(session)).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void lifecycleLogsKeepCategoriesAndDropSecrets(CapturedOutput output) throws Exception {
        String sentinelPassword = "Sentinel-password-value-91!";
        MvcResult session = login(user, OLD);
        String raw = cookieOf(session);
        String access = accessOf(session);
        login(user, sentinelPassword);
        String rotated = cookieOf(refresh(raw));
        refresh("sentinel-malformed-token-value");
        refresh(generator.generate().rawToken());
        refresh(raw);
        refresh(rotated);
        MvcResult next = login(user, OLD);
        changePassword(accessOf(next), OLD, NEXT);

        String hex = HexFormat.of().formatHex(generator.hashPresentedToken(raw));
        assertThat(output.getAll())
                .contains("auth.login.succeeded", "auth.login.failed category=invalid_credentials",
                        "auth.refresh.rotated", "auth.refresh.rejected category=malformed",
                        "auth.refresh.rejected category=unknown", "auth.refresh.reuse_detected",
                        "auth.refresh.rejected category=revoked",
                        "auth.session.revoke_all", "reason=PASSWORD_CHANGE")
                .doesNotContain(raw, rotated, access, hex, sentinelPassword, OLD, NEXT,
                        "sentinel-malformed-token-value", "Bearer ", "Cookie:", "Set-Cookie",
                        "LoginRequest[", "ChangePasswordRequest[");
    }
}
