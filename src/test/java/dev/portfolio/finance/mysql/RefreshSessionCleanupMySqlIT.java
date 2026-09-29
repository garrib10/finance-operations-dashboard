package dev.portfolio.finance.mysql;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import dev.portfolio.finance.dto.auth.LoginRequest;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.repository.UserRepository;
import dev.portfolio.finance.security.RefreshCookieService.PresentedRefreshCookie;
import dev.portfolio.finance.security.RefreshTokenGenerator;
import dev.portfolio.finance.service.AuthService;
import dev.portfolio.finance.service.RefreshSessionCleanupService;
import dev.portfolio.finance.service.RefreshSessionService;

/** Cleanup cutoff, cascade, and index usage on real MySQL. */
@ExtendWith(OutputCaptureExtension.class)
class RefreshSessionCleanupMySqlIT extends MySqlIntegrationTestBase {

    private static final String PASSWORD = "River meadow lantern 42!";

    @Autowired private RefreshSessionCleanupService cleanup;
    @Autowired private AuthService authService;
    @Autowired private RefreshSessionService sessions;
    @Autowired private RefreshTokenGenerator generator;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder encoder;

    private User user;

    @BeforeEach
    void setUp() {
        // Earlier IT classes may leave families behind; start from a clean table.
        jdbc.update("DELETE FROM refresh_sessions");
        user = users.saveAndFlush(new User("Clean", "Up", "cleanup-" + UUID.randomUUID() + "@example.com",
                encoder.encode(PASSWORD)));
    }

    private String loginAt(Instant at) {
        clock.set(at);
        return authService.login(new LoginRequest(user.getEmail(), PASSWORD)).rawRefreshToken();
    }

    private boolean exists(String raw) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE token_hash = ?", Long.class,
                (Object) generator.hashPresentedToken(raw)) > 0;
    }

    @Test
    void deletesOnlyFamiliesPastExpirationPlusRetentionWithHistory(CapturedOutput output) {
        Instant now = Instant.parse("2026-09-27T03:30:00Z");
        // Expires exactly at now - 7d - 1s: eligible.
        String eligible = loginAt(now.minus(Duration.ofDays(37)).minusSeconds(1));
        String rotatedFrom = loginAt(now.minus(Duration.ofDays(40)));
        String rotatedTo = sessions.refresh(new PresentedRefreshCookie(PresentedRefreshCookie.State.PRESENT,
                rotatedFrom)).issued().rawRefreshToken();
        // Expires exactly at the cutoff: kept (strictly older only).
        String boundary = loginAt(now.minus(Duration.ofDays(37)));
        // Expired but within retention: kept.
        String withinRetention = loginAt(now.minus(Duration.ofDays(33)));
        // Revoked but not expired: kept.
        String revokedActive = loginAt(now.minus(Duration.ofDays(1)));
        sessions.logout(new PresentedRefreshCookie(PresentedRefreshCookie.State.PRESENT, revokedActive));
        clock.set(now);

        int deleted = cleanup.cleanup();

        assertThat(deleted).isEqualTo(2);
        assertThat(exists(eligible)).isFalse();
        assertThat(exists(rotatedFrom)).isFalse();
        assertThat(exists(rotatedTo)).isFalse();
        assertThat(exists(boundary)).isTrue();
        assertThat(exists(withinRetention)).isTrue();
        assertThat(exists(revokedActive)).isTrue();
        assertThat(cleanup.cleanup()).isZero();
        assertThat(output.getAll()).contains("auth.session.cleanup category=completed deleted=2")
                .doesNotContain(eligible, rotatedFrom, boundary);
    }

    @Test
    void cleanupQueryCanUseTheExpirationIndex() {
        List<String> possibleKeys = jdbc.queryForList(
                "EXPLAIN SELECT id FROM refresh_sessions WHERE expires_at < NOW(6) ORDER BY expires_at LIMIT 500")
                .stream().map(row -> String.valueOf(row.get("possible_keys"))).toList();

        assertThat(possibleKeys).anySatisfy(keys -> assertThat(keys).contains("idx_refresh_sessions_expires_at"));
    }
}
