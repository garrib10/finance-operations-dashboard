package dev.portfolio.finance.integration;

import static dev.portfolio.finance.support.AuthRequests.protectedAuth;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import dev.portfolio.finance.entity.RefreshSession;
import dev.portfolio.finance.entity.RefreshSessionRevocationReason;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.repository.RefreshSessionRepository;
import dev.portfolio.finance.repository.RefreshTokenRepository;
import dev.portfolio.finance.repository.UserRepository;
import dev.portfolio.finance.security.RefreshCookieService;
import dev.portfolio.finance.security.RefreshTokenGenerator;
import dev.portfolio.finance.service.RefreshSessionService;
import dev.portfolio.finance.storage.ProfilePhotoStorage;
import dev.portfolio.finance.support.MutableClock;
import dev.portfolio.finance.support.ProfilePhotoImages;
import jakarta.servlet.http.Cookie;
import tools.jackson.databind.json.JsonMapper;

/** Password change revokes every family; other account changes never do. (H2) */
@SpringBootTest(properties = {
        "app.profile-photo.enabled=true", "app.profile-photo.cloud-name=test-cloud",
        "app.profile-photo.api-key=test-key", "app.profile-photo.api-secret=test-secret",
        "app.profile-photo.key-prefix=fintrack/test/profile-photos"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
@Import(PasswordChangeSessionIntegrationTest.ClockConfig.class)
class PasswordChangeSessionIntegrationTest {

    private static final String OLD = "River meadow lantern 42!";
    private static final String NEXT = "Another quiet forest 73!";

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
    @Autowired private JsonMapper mapper;
    @Autowired private PasswordEncoder encoder;
    @Autowired private UserRepository users;
    @Autowired private RefreshSessionRepository sessions;
    @Autowired private RefreshTokenRepository tokens;
    @Autowired private RefreshTokenGenerator generator;
    @Autowired private RefreshCookieService cookies;
    @Autowired private RefreshSessionService refreshSessions;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mappings;
    @MockitoBean private ProfilePhotoStorage storage;

    private User user;
    private User other;

    @BeforeEach
    void setUp() {
        clock.set(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        String suffix = UUID.randomUUID().toString();
        user = users.saveAndFlush(new User("Pass", "Word", "pw-" + suffix + "@example.com", encoder.encode(OLD)));
        other = users.saveAndFlush(new User("Other", "User", "pw-other-" + suffix + "@example.com", encoder.encode(OLD)));
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

    private RefreshSession familyOf(String raw) {
        String id = tokens.findByTokenHash(generator.hashPresentedToken(raw)).orElseThrow().getSession().getId();
        return sessions.findById(id).orElseThrow();
    }

    private int refreshStatus(String raw) throws Exception {
        return mvc.perform(protectedAuth(post("/api/auth/refresh")).cookie(new Cookie(cookies.cookieName(), raw)))
                .andReturn().getResponse().getStatus();
    }

    private MvcResult changePassword(String access, String current, String next) throws Exception {
        return mvc.perform(post("/api/account/password").header("Authorization", "Bearer " + access)
                .contentType("application/json")
                .content(mapper.writeValueAsString(new dev.portfolio.finance.dto.account.ChangePasswordRequest(
                        current, next)))).andReturn();
    }

    private int revokeAllInTransaction(User account) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            users.findByIdForUpdate(account.getId());
            return refreshSessions.revokeAllForUser(account.getId(), RefreshSessionRevocationReason.PASSWORD_CHANGE);
        });
    }

    @Test
    void revokeAllHandlesZeroOneAndManyFamiliesAndSkipsRevokedAndExpired() throws Exception {
        assertThat(revokeAllInTransaction(user)).isZero();

        String first = cookieOf(login(user, OLD));
        assertThat(revokeAllInTransaction(user)).isEqualTo(1);
        assertThat(familyOf(first).getRevocationReason()).isEqualTo(RefreshSessionRevocationReason.PASSWORD_CHANGE);

        String expired = cookieOf(login(user, OLD));
        clock.advance(Duration.ofDays(31));
        String loggedOut = cookieOf(login(user, OLD));
        mvc.perform(protectedAuth(post("/api/auth/logout")).cookie(new Cookie(cookies.cookieName(), loggedOut)));
        String a = cookieOf(login(user, OLD));
        String b = cookieOf(login(user, OLD));
        Instant firstRevokedAt = familyOf(first).getRevokedAt();
        clock.advance(Duration.ofMinutes(3));

        assertThat(revokeAllInTransaction(user)).isEqualTo(2);

        assertThat(familyOf(a).getRevokedAt()).isEqualTo(clock.instant());
        assertThat(familyOf(b).getRevocationReason()).isEqualTo(RefreshSessionRevocationReason.PASSWORD_CHANGE);
        assertThat(familyOf(loggedOut).getRevocationReason()).isEqualTo(RefreshSessionRevocationReason.LOGOUT);
        assertThat(familyOf(first).getRevokedAt()).isEqualTo(firstRevokedAt);
        assertThat(familyOf(expired).isRevoked()).isFalse();
        assertThat(familyOf(expired).isActiveAt(clock.instant())).isFalse();
    }

    @Test
    void revokeAllRefusesToRunOutsideATransaction() {
        assertThatThrownBy(() -> refreshSessions.revokeAllForUser(user.getId(),
                RefreshSessionRevocationReason.PASSWORD_CHANGE))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void passwordChangeRevokesAllOwnFamiliesAndClearsCookie() throws Exception {
        MvcResult laptop = login(user, OLD);
        String phone = cookieOf(login(user, OLD));
        String otherUser = cookieOf(login(other, OLD));
        long history = tokens.count();

        MvcResult changed = changePassword(accessOf(laptop), OLD, NEXT);

        assertThat(changed.getResponse().getStatus()).isEqualTo(204);
        Cookie cleared = changed.getResponse().getCookie(cookies.cookieName());
        assertThat(cleared.getValue()).isEmpty();
        assertThat(cleared.getMaxAge()).isZero();
        assertThat(familyOf(cookieOf(laptop)).getRevocationReason())
                .isEqualTo(RefreshSessionRevocationReason.PASSWORD_CHANGE);
        assertThat(familyOf(phone).getRevokedAt()).isEqualTo(clock.instant());
        assertThat(familyOf(otherUser).isRevoked()).isFalse();
        assertThat(tokens.count()).isEqualTo(history);
        assertThat(changed.getResponse().getContentAsString()).isEmpty();

        assertThat(refreshStatus(phone)).isEqualTo(401);
        assertThat(refreshStatus(otherUser)).isEqualTo(200);
        assertThat(mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + accessOf(laptop)))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(login(user, OLD).getResponse().getStatus()).isEqualTo(401);
        MvcResult fresh = login(user, NEXT);
        assertThat(fresh.getResponse().getStatus()).isEqualTo(200);
        assertThat(familyOf(cookieOf(fresh)).isRevoked()).isFalse();
    }

    @Test
    void rejectedPasswordChangesChangeNothingAndKeepCookie() throws Exception {
        MvcResult session = login(user, OLD);
        String hash = users.findById(user.getId()).orElseThrow().getPasswordHash();

        for (String[] attempt : List.of(new String[] {"not the current one", NEXT}, new String[] {OLD, "short"},
                new String[] {OLD, OLD})) {
            MvcResult result = changePassword(accessOf(session), attempt[0], attempt[1]);
            assertThat(result.getResponse().getStatus()).isEqualTo(400);
            assertThat(result.getResponse().getHeader("Set-Cookie")).isNull();
        }
        assertThat(users.findById(user.getId()).orElseThrow().getPasswordHash()).isEqualTo(hash);
        assertThat(familyOf(cookieOf(session)).isRevoked()).isFalse();
    }

    @Test
    void passwordChangeStillRequiresBearerAuthentication() throws Exception {
        String raw = cookieOf(login(user, OLD));

        MvcResult result = mvc.perform(protectedAuth(post("/api/account/password"))
                .cookie(new Cookie(cookies.cookieName(), raw)).contentType("application/json")
                .content("{\"currentPassword\":\"" + OLD + "\",\"newPassword\":\"" + NEXT + "\"}")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(result.getResponse().getHeader("Set-Cookie")).isNull();
        assertThat(familyOf(raw).isRevoked()).isFalse();
        assertThat(encoder.matches(OLD, users.findById(user.getId()).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test
    void profilePreferencesAndPhotoChangesDoNotRevoke() throws Exception {
        MvcResult session = login(user, OLD);
        String access = accessOf(session);

        assertThat(mvc.perform(put("/api/account/profile").header("Authorization", "Bearer " + access)
                .contentType("application/json")
                .content("{\"firstName\":\"New\",\"lastName\":\"Name\",\"displayName\":\"New Name\"}"))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(mvc.perform(put("/api/account/preferences").header("Authorization", "Bearer " + access)
                .contentType("application/json").content("{\"dateFormat\":\"ISO\",\"transactionPageSize\":25}"))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(mvc.perform(multipart(HttpMethod.PUT, "/api/account/photo")
                .file(new MockMultipartFile("photo", "p.png", "image/png", ProfilePhotoImages.image("png", 40, 20)))
                .header("Authorization", "Bearer " + access)).andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(mvc.perform(delete("/api/account/photo").header("Authorization", "Bearer " + access))
                .andReturn().getResponse().getStatus()).isEqualTo(200);

        assertThat(familyOf(cookieOf(session)).isRevoked()).isFalse();
        assertThat(refreshStatus(cookieOf(session))).isEqualTo(200);
    }

    @Test
    void noEndpointExposesRevocationBySessionOrUserId() {
        List<String> patterns = mappings.getHandlerMethods().entrySet().stream()
                .filter(entry -> entry.getValue() instanceof HandlerMethod)
                .flatMap(entry -> entry.getKey().getPatternValues().stream())
                .toList();

        assertThat(patterns).noneMatch(path -> path.contains("revoke") || path.contains("session")
                || path.contains("{userId}") || path.contains("{sessionId}"));
        assertThat(patterns).filteredOn(path -> path.startsWith("/api/auth"))
                .containsExactlyInAnyOrder("/api/auth/register", "/api/auth/login", "/api/auth/refresh",
                        "/api/auth/logout", "/api/auth/me");
    }

    @Test
    void passwordChangeLogsRevokeAllWithoutSecrets(CapturedOutput output) throws Exception {
        MvcResult session = login(user, OLD);
        String raw = cookieOf(session);
        String access = accessOf(session);

        changePassword(access, OLD, NEXT);

        assertThat(output.getAll())
                .contains("auth.session.revoke_all userId=" + user.getId() + " reason=PASSWORD_CHANGE count=1")
                .doesNotContain(OLD, NEXT, raw, access, "ChangePasswordRequest[",
                        java.util.HexFormat.of().formatHex(generator.hashPresentedToken(raw)));
    }
}
