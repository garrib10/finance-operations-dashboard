package dev.portfolio.finance.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import dev.portfolio.finance.support.MutableClock;
import dev.portfolio.finance.support.TestDataFactory;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

class RefreshSessionTest {

    private static final Instant START = Instant.parse("2026-09-27T12:00:00.123456789Z");
    private static final Duration THIRTY_DAYS = Duration.ofDays(30);

    private final MutableClock clock = new MutableClock(START);
    private final User user = TestDataFactory.createUser();

    @Test
    void startsWithRandomUuidAndDeterministicUtcTimesAtDatabasePrecision() {
        RefreshSession session = RefreshSession.start(user, clock, THIRTY_DAYS);
        RefreshSession other = RefreshSession.start(user, clock, THIRTY_DAYS);

        assertThat(session.getId())
                .hasSize(36)
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
                .isNotEqualTo(other.getId());
        assertThat(session.isNew()).isTrue();
        assertThat(session.getUser()).isSameAs(user);
        assertThat(session.getCreatedAt()).isEqualTo(Instant.parse("2026-09-27T12:00:00.123456Z"));
        assertThat(session.getExpiresAt()).isEqualTo(Instant.parse("2026-10-27T12:00:00.123456Z"));
        assertThat(session.getRevokedAt()).isNull();
        assertThat(session.getRevocationReason()).isNull();
        assertThat(session.isRevoked()).isFalse();
    }

    @Test
    void isActiveOnlyBeforeAbsoluteExpiration() {
        RefreshSession session = RefreshSession.start(user, clock, THIRTY_DAYS);
        Instant expiresAt = session.getExpiresAt();

        assertThat(session.isActiveAt(session.getCreatedAt())).isTrue();
        assertThat(session.isActiveAt(expiresAt.minusNanos(1000))).isTrue();
        assertThat(session.isActiveAt(expiresAt)).isFalse();
        assertThat(session.isActiveAt(expiresAt.plusSeconds(1))).isFalse();
        assertThatThrownBy(() -> session.isActiveAt(null)).isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @EnumSource(RefreshSessionRevocationReason.class)
    void revokesWithClockTimeAndControlledReason(RefreshSessionRevocationReason reason) {
        RefreshSession session = RefreshSession.start(user, clock, THIRTY_DAYS);
        clock.advance(Duration.ofHours(2));

        session.revoke(reason, clock);

        assertThat(session.getRevokedAt()).isEqualTo(Instant.parse("2026-09-27T14:00:00.123456Z"));
        assertThat(session.getRevocationReason()).isEqualTo(reason);
        assertThat(session.isRevoked()).isTrue();
        assertThat(session.isActiveAt(clock.instant())).isFalse();
        assertThat(session.isActiveAt(session.getCreatedAt())).isFalse();
    }

    @Test
    void approvedReasonsFitTheDatabaseColumn() {
        assertThat(RefreshSessionRevocationReason.values())
                .extracting(Enum::name)
                .containsExactly("LOGOUT", "PASSWORD_CHANGE", "REUSE_DETECTED")
                .allSatisfy(name -> assertThat(name.length()).isLessThanOrEqualTo(24));
    }

    @Test
    void revocationCannotBeOverwrittenOrLeftIncomplete() {
        RefreshSession session = RefreshSession.start(user, clock, THIRTY_DAYS);

        assertThatThrownBy(() -> session.revoke(null, clock)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> session.revoke(RefreshSessionRevocationReason.LOGOUT, null))
                .isInstanceOf(NullPointerException.class);
        assertThat(session.getRevokedAt()).isNull();
        assertThat(session.getRevocationReason()).isNull();

        session.revoke(RefreshSessionRevocationReason.LOGOUT, clock);
        clock.advance(Duration.ofMinutes(1));

        assertThatThrownBy(() -> session.revoke(RefreshSessionRevocationReason.REUSE_DETECTED, clock))
                .isInstanceOf(IllegalStateException.class);
        assertThat(session.getRevocationReason()).isEqualTo(RefreshSessionRevocationReason.LOGOUT);
        assertThat(session.getRevokedAt()).isEqualTo(Instant.parse("2026-09-27T12:00:00.123456Z"));
    }

    @Test
    void revocationCannotPrecedeCreation() {
        RefreshSession session = RefreshSession.start(user, clock, THIRTY_DAYS);
        clock.set(START.minusSeconds(1));

        assertThatThrownBy(() -> session.revoke(RefreshSessionRevocationReason.LOGOUT, clock))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(session.isRevoked()).isFalse();
    }

    @Test
    void rejectsExpirationThatDoesNotFollowCreation() {
        assertThatThrownBy(() -> RefreshSession.start(user, clock, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RefreshSession.start(user, clock, Duration.ofDays(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        // Below DATETIME(6) precision, so it would persist equal to creation.
        assertThatThrownBy(() -> RefreshSession.start(user, clock, Duration.ofNanos(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingInputs() {
        assertThatThrownBy(() -> RefreshSession.start(null, clock, THIRTY_DAYS))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RefreshSession.start(user, null, THIRTY_DAYS))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RefreshSession.start(user, clock, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void persistenceCallbacksClearNewFlag() {
        RefreshSession session = RefreshSession.start(user, clock, THIRTY_DAYS);
        session.markPersisted();
        assertThat(session.isNew()).isFalse();
    }

    @Test
    void stringAndJsonRepresentationsExposeNothing() {
        RefreshSession session = RefreshSession.start(user, clock, THIRTY_DAYS);
        session.revoke(RefreshSessionRevocationReason.PASSWORD_CHANGE, clock);

        assertThat(session.toString())
                .isEqualTo("RefreshSession[details redacted]")
                .doesNotContain(session.getId(), user.getEmail(), user.getPasswordHash());
        String json = JsonMapper.builder()
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS).build()
                .writeValueAsString(session);
        assertThat(json).isEqualTo("{}");
    }
}
