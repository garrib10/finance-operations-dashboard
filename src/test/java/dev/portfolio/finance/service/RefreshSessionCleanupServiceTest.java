package dev.portfolio.finance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import dev.portfolio.finance.config.AuthSessionProperties;
import dev.portfolio.finance.entity.RefreshSession;
import dev.portfolio.finance.entity.RefreshSessionRevocationReason;
import dev.portfolio.finance.entity.RefreshToken;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.repository.RefreshSessionRepository;
import dev.portfolio.finance.repository.RefreshTokenRepository;
import dev.portfolio.finance.repository.UserRepository;
import dev.portfolio.finance.support.MutableClock;

@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class RefreshSessionCleanupServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-27T03:30:00Z");
    private static final Duration TTL = Duration.ofDays(30);

    @Autowired private RefreshSessionRepository sessions;
    @Autowired private RefreshTokenRepository tokens;
    @Autowired private UserRepository users;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private JdbcTemplate jdbc;

    private final MutableClock clock = new MutableClock(NOW);
    private User user;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM refresh_sessions");
        user = users.saveAndFlush(new User("Clean", "Up", "clean-" + UUID.randomUUID() + "@example.com", "hash"));
    }

    private static AuthSessionProperties properties(boolean enabled) {
        return new AuthSessionProperties(Duration.ofMinutes(5), TTL, true, AuthSessionProperties.SameSite.LAX,
                enabled, Duration.ofDays(7));
    }

    private RefreshSessionCleanupService service(boolean enabled, int batchSize, int maxBatches) {
        return new RefreshSessionCleanupService(sessions, properties(enabled), clock, transactions,
                batchSize, maxBatches);
    }

    /** A family created {@code age} before NOW, with one token row. */
    private String family(Duration age, boolean revoked) {
        MutableClock at = new MutableClock(NOW.minus(age));
        RefreshSession session = sessions.saveAndFlush(RefreshSession.start(user, at, TTL));
        byte[] hash = new byte[32];
        new java.security.SecureRandom().nextBytes(hash);
        tokens.saveAndFlush(RefreshToken.issue(session, hash, at));
        if (revoked) {
            session.revoke(RefreshSessionRevocationReason.LOGOUT, at);
            sessions.saveAndFlush(session);
        }
        return session.getId();
    }

    private boolean exists(String id) {
        return sessions.existsById(id);
    }

    private long tokenRows(String id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE session_id = ?", Long.class, id);
    }

    @Test
    void disabledCleanupDeletesNothing(CapturedOutput output) {
        String old = family(Duration.ofDays(60), false);

        assertThat(service(false, 500, 20).cleanup()).isZero();

        assertThat(exists(old)).isTrue();
        assertThat(output.getAll()).contains("auth.session.cleanup category=disabled");
    }

    @Test
    void deletesOnlyFamiliesPastExpirationPlusRetentionIncludingRevokedOnes() {
        String active = family(Duration.ofDays(1), false);
        String revokedRecent = family(Duration.ofDays(2), true);
        String expiredWithinRetention = family(Duration.ofDays(36), false);
        String boundary = family(Duration.ofDays(37), false);
        String eligible = family(Duration.ofDays(37).plusSeconds(1), false);
        String eligibleRevoked = family(Duration.ofDays(50), true);

        assertThat(service(true, 500, 20).cleanup()).isEqualTo(2);

        assertThat(exists(active)).isTrue();
        assertThat(exists(revokedRecent)).isTrue();
        assertThat(exists(expiredWithinRetention)).isTrue();
        assertThat(exists(boundary)).isTrue();
        assertThat(exists(eligible)).isFalse();
        assertThat(exists(eligibleRevoked)).isFalse();
        assertThat(tokenRows(eligible)).isZero();
        assertThat(tokenRows(eligibleRevoked)).isZero();
        assertThat(tokenRows(active)).isEqualTo(1);
    }

    @Test
    void fixedClockControlsTheCutoff() {
        String family = family(Duration.ofDays(37).plusSeconds(1), false);
        clock.set(NOW.minusSeconds(2));

        assertThat(service(true, 500, 20).cleanup()).isZero();
        assertThat(exists(family)).isTrue();

        clock.set(NOW);
        assertThat(service(true, 500, 20).cleanup()).isEqualTo(1);
    }

    @Test
    void batchesAreBoundedAndRepeatedRunsAreSafe(CapturedOutput output) {
        List<String> eligible = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            eligible.add(family(Duration.ofDays(40 + i), false));
        }

        RefreshSessionCleanupService bounded = service(true, 2, 2);
        assertThat(bounded.cleanup()).isEqualTo(4);
        assertThat(eligible.stream().filter(this::exists).count()).isEqualTo(1);
        // Oldest first: the newest eligible family is left for the next run.
        assertThat(exists(eligible.getFirst())).isTrue();
        assertThat(output.getAll()).contains("auth.session.cleanup category=completed deleted=4 batches=2");

        assertThat(bounded.cleanup()).isEqualTo(1);
        assertThat(bounded.cleanup()).isZero();
        assertThat(eligible).noneMatch(this::exists);
    }

    @Test
    void failureIsLoggedSafelyAndNeverThrown(CapturedOutput output) {
        RefreshSessionRepository failing = mock(RefreshSessionRepository.class);
        when(failing.findIdsExpiredBefore(any(), any())).thenThrow(
                new DataAccessResourceFailureException("connection refused: token_hash=deadbeef secret-detail"));
        RefreshSessionCleanupService service = new RefreshSessionCleanupService(failing, properties(true), clock,
                transactions, 500, 20);

        assertThat(service.cleanup()).isZero();

        verify(failing, never()).deleteExpiredByIds(any(), any());
        assertThat(output.getAll())
                .contains("auth.session.cleanup category=failed deleted=0 batches=0 "
                        + "error=DataAccessResourceFailureException")
                .doesNotContain("connection refused", "deadbeef", "secret-detail");
    }

    @Test
    void scheduledEntryPointRunsTheSameCleanup() {
        String eligible = family(Duration.ofDays(45), false);

        service(true, 500, 20).scheduledCleanup();

        assertThat(exists(eligible)).isFalse();
    }
}
