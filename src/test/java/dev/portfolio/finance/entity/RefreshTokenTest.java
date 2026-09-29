package dev.portfolio.finance.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import dev.portfolio.finance.support.MutableClock;
import dev.portfolio.finance.support.TestDataFactory;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

class RefreshTokenTest {

    private static final Instant START = Instant.parse("2026-09-27T12:00:00Z");

    private final MutableClock clock = new MutableClock(START);
    private final RefreshSession session =
            RefreshSession.start(TestDataFactory.createUser(), clock, Duration.ofDays(30));

    private static byte[] hash(int seed) {
        byte[] hash = new byte[32];
        Arrays.fill(hash, (byte) seed);
        return hash;
    }

    @Test
    void issuesWithExactHashCopyAndClockTime() {
        byte[] input = hash(7);
        clock.advance(Duration.ofMinutes(5));

        RefreshToken token = RefreshToken.issue(session, input, clock);
        input[0] = 99;

        assertThat(token.getSession()).isSameAs(session);
        assertThat(token.getTokenHash()).isEqualTo(hash(7));
        assertThat(token.getCreatedAt()).isEqualTo(START.plus(Duration.ofMinutes(5)));
        assertThat(token.getConsumedAt()).isNull();
        assertThat(token.isConsumed()).isFalse();
        assertThat(token.getId()).isNull();
    }

    @Test
    void returnedHashIsDefensiveCopy() {
        RefreshToken token = RefreshToken.issue(session, hash(1), clock);
        byte[] returned = token.getTokenHash();
        returned[0] = 42;

        assertThat(token.getTokenHash()).isEqualTo(hash(1)).isNotSameAs(token.getTokenHash());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 16, 31, 33, 43, 64})
    void rejectsHashesThatAreNotExactly32Bytes(int length) {
        assertThatThrownBy(() -> RefreshToken.issue(session, new byte[length], clock))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Refresh token hash must be exactly 32 bytes");
    }

    @Test
    void rejectsMissingInputs() {
        assertThatThrownBy(() -> RefreshToken.issue(session, null, clock))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RefreshToken.issue(null, hash(1), clock))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RefreshToken.issue(session, hash(1), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsIssuanceOutsideAnActiveSession() {
        clock.set(session.getExpiresAt());
        assertThatThrownBy(() -> RefreshToken.issue(session, hash(1), clock))
                .isInstanceOf(IllegalStateException.class);

        clock.set(START.minusSeconds(1));
        assertThatThrownBy(() -> RefreshToken.issue(session, hash(1), clock))
                .isInstanceOf(IllegalStateException.class);

        clock.set(START);
        session.revoke(RefreshSessionRevocationReason.LOGOUT, clock);
        assertThatThrownBy(() -> RefreshToken.issue(session, hash(1), clock))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void consumesOnceAtClockTime() {
        RefreshToken token = RefreshToken.issue(session, hash(1), clock);
        clock.advance(Duration.ofMinutes(4));

        token.markConsumed(clock);

        assertThat(token.isConsumed()).isTrue();
        assertThat(token.getConsumedAt()).isEqualTo(START.plus(Duration.ofMinutes(4)));

        clock.advance(Duration.ofMinutes(1));
        assertThatThrownBy(() -> token.markConsumed(clock))
                .isInstanceOf(IllegalStateException.class);
        assertThat(token.getConsumedAt()).isEqualTo(START.plus(Duration.ofMinutes(4)));
    }

    @Test
    void consumptionCannotPrecedeCreation() {
        RefreshToken token = RefreshToken.issue(session, hash(1), clock);
        clock.set(START.minusNanos(1000));

        assertThatThrownBy(() -> token.markConsumed(clock))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(token.isConsumed()).isFalse();
        assertThatThrownBy(() -> token.markConsumed(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void entityCannotHoldRawOrEncodedTokenText() {
        List<Class<?>> textTypes = List.of(String.class, CharSequence.class, char[].class);
        List<Class<?>> fieldTypes = Arrays.stream(RefreshToken.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .<Class<?>>map(field -> field.getType())
                .toList();
        List<Class<?>> parameterTypes = Arrays.stream(RefreshToken.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .<Class<?>>flatMap(method -> Arrays.stream(method.getParameterTypes()))
                .toList();

        assertThat(fieldTypes).doesNotContainAnyElementsOf(textTypes);
        assertThat(parameterTypes).doesNotContainAnyElementsOf(textTypes);
    }

    @Test
    void stringAndJsonRepresentationsExposeNothing() {
        byte[] secretLookingHash = hash(0x5a);
        RefreshToken token = RefreshToken.issue(session, secretLookingHash, clock);
        String hex = HexFormat.of().formatHex(secretLookingHash);

        assertThat(token.toString())
                .isEqualTo("RefreshToken[details redacted]")
                .doesNotContain(hex, Arrays.toString(secretLookingHash));
        String json = JsonMapper.builder()
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS).build()
                .writeValueAsString(token);
        assertThat(json).isEqualTo("{}");
    }
}
