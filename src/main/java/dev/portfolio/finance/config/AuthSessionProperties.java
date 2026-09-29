package dev.portfolio.finance.config;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.validation.annotation.Validated;

/**
 * Access-token and refresh-session policy. Defaults are the approved production values.
 * Cookie name, path, and domain are fixed constants, not configuration.
 */
@Validated
@ConfigurationProperties("app.auth-session")
public record AuthSessionProperties(
        @NotNull @DefaultValue("300000") @DurationUnit(ChronoUnit.MILLIS) Duration accessTokenLifetime,
        @NotNull @DefaultValue("2592000") @DurationUnit(ChronoUnit.SECONDS) Duration refreshSessionTtl,
        @DefaultValue("true") boolean refreshCookieSecure,
        @NotNull @DefaultValue("Lax") SameSite refreshCookieSameSite,
        @DefaultValue("true") boolean cleanupEnabled,
        @NotNull @DefaultValue("7") @DurationUnit(ChronoUnit.DAYS) Duration retention
) {
    public static final Duration MIN_ACCESS_TOKEN_LIFETIME = Duration.ofMinutes(1);
    public static final Duration MAX_ACCESS_TOKEN_LIFETIME = Duration.ofHours(1);
    public static final Duration MAX_REFRESH_SESSION_TTL = Duration.ofDays(30);
    public static final Duration MIN_RETENTION = Duration.ofDays(1);
    public static final Duration MAX_RETENTION = Duration.ofDays(30);

    public static final String LOCAL_REFRESH_COOKIE_NAME = "fintrack_refresh";
    /** The __Secure- prefix is only valid on cookies that carry the Secure attribute. */
    public static final String SECURE_REFRESH_COOKIE_NAME = "__Secure-fintrack_refresh";
    public static final String REFRESH_COOKIE_PATH = "/api/auth";

    /** None is excluded: the approved topology is a same-origin /api proxy. */
    public enum SameSite {
        STRICT("Strict"),
        LAX("Lax");

        private final String attributeValue;

        SameSite(String attributeValue) {
            this.attributeValue = attributeValue;
        }

        public String attributeValue() {
            return attributeValue;
        }
    }

    @AssertTrue(message = "app.auth-session.access-token-lifetime (JWT_EXPIRATION_MS) "
            + "must be between 1 and 60 minutes")
    public boolean isAccessTokenLifetimeValid() {
        return accessTokenLifetime == null || within(accessTokenLifetime,
                MIN_ACCESS_TOKEN_LIFETIME, MAX_ACCESS_TOKEN_LIFETIME);
    }

    @AssertTrue(message = "app.auth-session.refresh-session-ttl (REFRESH_SESSION_TTL_SECONDS) "
            + "must be positive and at most 30 days")
    public boolean isRefreshSessionTtlValid() {
        return refreshSessionTtl == null || within(refreshSessionTtl,
                Duration.ofSeconds(1), MAX_REFRESH_SESSION_TTL);
    }

    @AssertTrue(message = "app.auth-session.refresh-session-ttl (REFRESH_SESSION_TTL_SECONDS) "
            + "must exceed app.auth-session.access-token-lifetime (JWT_EXPIRATION_MS)")
    public boolean isRefreshSessionLongerThanAccessToken() {
        return refreshSessionTtl == null || accessTokenLifetime == null
                || refreshSessionTtl.compareTo(accessTokenLifetime) > 0;
    }

    @AssertTrue(message = "app.auth-session.retention (REFRESH_SESSION_RETENTION_DAYS) "
            + "must be between 1 and 30 days")
    public boolean isRetentionValid() {
        return retention == null || within(retention, MIN_RETENTION, MAX_RETENTION);
    }

    public String refreshCookieName() {
        return refreshCookieSecure ? SECURE_REFRESH_COOKIE_NAME : LOCAL_REFRESH_COOKIE_NAME;
    }

    private static boolean within(Duration value, Duration min, Duration max) {
        return value.compareTo(min) >= 0 && value.compareTo(max) <= 0;
    }

    @Override
    public String toString() {
        return "AuthSessionProperties[configuration redacted]";
    }
}
