package dev.portfolio.finance.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.support.MutableClock;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.test.util.ReflectionTestUtils;

class JwtServiceHardeningTest {

    private static final byte[] KEY = new byte[32];
    private static final String SECRET = Base64.getEncoder().encodeToString(KEY);
    private static final SecretKey SIGNING_KEY = Keys.hmacShaKeyFor(KEY);
    private static final Instant NOW = Instant.parse("2026-09-27T12:00:00Z");

    private final MutableClock clock = new MutableClock(NOW);
    private final JwtService jwtService = new JwtService(SECRET, Duration.ofMinutes(5), clock);

    private static User user(long id) {
        User user = new User("Jwt", "User", "jwt@example.com", "hash");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Claims claims(String token) {
        return Jwts.parser().verifyWith(SIGNING_KEY).clock(() -> Date.from(clock.instant()))
                .build().parseSignedClaims(token).getPayload();
    }

    @Test
    void issuesFiveMinuteTokenWithRequiredClaims() {
        String token = jwtService.generateToken(user(42));
        Claims claims = claims(token);

        assertThat(claims.getSubject()).isEqualTo("jwt@example.com");
        assertThat(claims.get("userId", Long.class)).isEqualTo(42L);
        assertThat(claims.getId()).matches("[0-9a-f-]{36}");
        assertThat(claims.getIssuedAt().toInstant()).isEqualTo(NOW);
        assertThat(claims.getExpiration().toInstant()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
        assertThat(jwtService.getExpirationSeconds()).isEqualTo(300);
        assertThat(jwtService.getExpirationMs()).isEqualTo(300_000);
        assertThat(jwtService.validate(token)).isEqualTo(AccessTokenValidation.valid("jwt@example.com", 42L));
    }

    @Test
    void everyTokenHasUniqueJti() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            ids.add(claims(jwtService.generateToken(user(1))).getId());
        }
        assertThat(ids).hasSize(50);
    }

    @Test
    void reportsExpiredOnlyAfterLifetimeForValidlySignedToken() {
        String token = jwtService.generateToken(user(7));

        clock.advance(Duration.ofMinutes(5).minusSeconds(1));
        assertThat(jwtService.validate(token).status()).isEqualTo(AccessTokenValidation.Status.VALID);

        // JJWT treats exp as inclusive: valid at the instant, expired just after it.
        clock.advance(Duration.ofSeconds(1));
        assertThat(jwtService.validate(token).status()).isEqualTo(AccessTokenValidation.Status.VALID);
        clock.advance(Duration.ofMillis(1));
        AccessTokenValidation expired = jwtService.validate(token);
        assertThat(expired.status()).isEqualTo(AccessTokenValidation.Status.EXPIRED);
        assertThat(expired.email()).isNull();
        assertThat(expired.userId()).isNull();
        assertThat(jwtService.isTokenValid(token)).isFalse();
    }

    @Test
    void expiredTokenWithWrongSignatureIsInvalidNotExpired() {
        byte[] otherKey = new byte[32];
        otherKey[0] = 1;
        String foreign = new JwtService(Base64.getEncoder().encodeToString(otherKey), Duration.ofMinutes(5), clock)
                .generateToken(user(7));
        clock.advance(Duration.ofHours(1));

        assertThat(jwtService.validate(foreign).status()).isEqualTo(AccessTokenValidation.Status.INVALID);
    }

    @Test
    void tokensMissingRequiredClaimsAreInvalidEvenWhenExpired() {
        Date past = Date.from(NOW.minusSeconds(60));
        Date future = Date.from(NOW.plusSeconds(60));
        String noUserId = Jwts.builder().subject("jwt@example.com").id("jti").expiration(future)
                .signWith(SIGNING_KEY).compact();
        String noJti = Jwts.builder().subject("jwt@example.com").claim("userId", 1).expiration(future)
                .signWith(SIGNING_KEY).compact();
        String noSubject = Jwts.builder().id("jti").claim("userId", 1).expiration(future)
                .signWith(SIGNING_KEY).compact();
        String noExpiration = Jwts.builder().subject("jwt@example.com").id("jti").claim("userId", 1)
                .signWith(SIGNING_KEY).compact();
        String textUserId = Jwts.builder().subject("jwt@example.com").id("jti").claim("userId", "1")
                .expiration(future).signWith(SIGNING_KEY).compact();
        String expiredNoUserId = Jwts.builder().subject("jwt@example.com").id("jti").expiration(past)
                .signWith(SIGNING_KEY).compact();

        for (String token : new String[] {noUserId, noJti, noSubject, noExpiration, textUserId, expiredNoUserId}) {
            assertThat(jwtService.validate(token).status()).isEqualTo(AccessTokenValidation.Status.INVALID);
        }
    }

    @Test
    void unsignedTokenIsInvalid() {
        String unsigned = Jwts.builder().subject("jwt@example.com").id("jti").claim("userId", 1)
                .expiration(Date.from(NOW.plusSeconds(60))).compact();
        assertThat(jwtService.validate(unsigned).status()).isEqualTo(AccessTokenValidation.Status.INVALID);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "not-a-jwt", "a.b.c", "eyJhbGciOiJIUzI1NiJ9.e30."})
    void malformedTokensAreInvalid(String token) {
        assertThat(jwtService.validate(token).status()).isEqualTo(AccessTokenValidation.Status.INVALID);
    }

    @Test
    void extractorsRejectInvalidTokensWithoutEchoingThem() {
        String token = jwtService.generateToken(user(9));
        clock.advance(Duration.ofMinutes(6));

        assertThatThrownBy(() -> jwtService.extractEmail(token))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Access token is not valid")
                .hasNoCause();
        assertThatThrownBy(() -> jwtService.extractUserId("bogus-token-value"))
                .hasMessageNotContaining("bogus-token-value");
    }

    @Test
    void validationResultNeverContainsTokenText() {
        String token = jwtService.generateToken(user(3));
        assertThat(jwtService.validate(token).toString()).doesNotContain(token);
    }
}
