package dev.portfolio.finance.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import dev.portfolio.finance.config.AuthSessionProperties;
import dev.portfolio.finance.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

/** Access JWTs only. Refresh tokens are opaque and never pass through this class. */
@Service
public class JwtService {

    private static final String USER_ID_CLAIM = "userId";

    private final SecretKey signingKey;
    private final Duration lifetime;
    private final Clock clock;
    private final JwtParser parser;

    @Autowired
    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            AuthSessionProperties properties,
            Clock clock
    ) {
        this(secret, properties.accessTokenLifetime(), clock);
    }

    public JwtService(String secret, long expirationMs) {
        this(secret, Duration.ofMillis(expirationMs), Clock.systemUTC());
    }

    public JwtService(String secret, Duration lifetime, Clock clock) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.lifetime = lifetime;
        this.clock = clock;
        this.parser = Jwts.parser()
                .verifyWith(signingKey)
                .clock(() -> Date.from(clock.instant()))
                .build();
    }

    public String generateToken(User user) {
        Instant now = clock.instant();

        return Jwts.builder()
                .subject(user.getEmail())
                .id(UUID.randomUUID().toString())
                .claim(USER_ID_CLAIM, user.getId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(lifetime)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Verifies signature and expiration once, then requires subject, numeric userId,
     * and jti. EXPIRED is reported only for a correctly signed, complete token.
     */
    public AccessTokenValidation validate(String token) {
        if (token == null || token.isBlank()) {
            return AccessTokenValidation.invalid();
        }
        try {
            Claims claims = parser.parseSignedClaims(token).getPayload();
            return hasRequiredClaims(claims)
                    ? AccessTokenValidation.valid(claims.getSubject(), userId(claims))
                    : AccessTokenValidation.invalid();
        } catch (ExpiredJwtException ex) {
            return hasRequiredClaims(ex.getClaims())
                    ? AccessTokenValidation.expired()
                    : AccessTokenValidation.invalid();
        } catch (JwtException | IllegalArgumentException ex) {
            return AccessTokenValidation.invalid();
        }
    }

    public boolean isTokenValid(String token) {
        return validate(token).isValid();
    }

    public String extractEmail(String token) {
        return requireValid(token).email();
    }

    public Long extractUserId(String token) {
        return requireValid(token).userId();
    }

    public long getExpirationMs() {
        return lifetime.toMillis();
    }

    public long getExpirationSeconds() {
        return lifetime.toSeconds();
    }

    private AccessTokenValidation requireValid(String token) {
        AccessTokenValidation result = validate(token);
        if (!result.isValid()) {
            throw new IllegalArgumentException("Access token is not valid");
        }
        return result;
    }

    private static boolean hasRequiredClaims(Claims claims) {
        return claims != null
                && claims.getSubject() != null && !claims.getSubject().isBlank()
                && claims.getId() != null && !claims.getId().isBlank()
                && claims.getExpiration() != null
                && userId(claims) != null;
    }

    private static Long userId(Claims claims) {
        Object value = claims.get(USER_ID_CLAIM);
        return value instanceof Integer || value instanceof Long ? ((Number) value).longValue() : null;
    }
}
