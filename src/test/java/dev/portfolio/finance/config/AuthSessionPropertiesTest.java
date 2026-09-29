package dev.portfolio.finance.config;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import dev.portfolio.finance.config.AuthSessionProperties.SameSite;

class AuthSessionPropertiesTest {

    private static final String PREFIX = "app.auth-session.";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AuthSessionConfig.class);

    private static String failure(Throwable throwable) {
        StringWriter text = new StringWriter();
        throwable.printStackTrace(new PrintWriter(text));
        return text.toString();
    }

    @Test
    void defaultsAreTheApprovedProductionPolicy() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            AuthSessionProperties config = context.getBean(AuthSessionProperties.class);
            assertThat(config.accessTokenLifetime()).isEqualTo(Duration.ofMinutes(5));
            assertThat(config.refreshSessionTtl()).isEqualTo(Duration.ofDays(30));
            assertThat(config.refreshCookieSecure()).isTrue();
            assertThat(config.refreshCookieSameSite()).isEqualTo(SameSite.LAX);
            assertThat(config.refreshCookieSameSite().attributeValue()).isEqualTo("Lax");
            assertThat(config.cleanupEnabled()).isTrue();
            assertThat(config.retention()).isEqualTo(Duration.ofDays(7));
            assertThat(config.refreshCookieName()).isEqualTo("__Secure-fintrack_refresh");
            assertThat(AuthSessionProperties.REFRESH_COOKIE_PATH).isEqualTo("/api/auth");
        });
    }

    @Test
    void bindsApprovedEnvironmentValuesInTheirDocumentedUnits() {
        runner.withPropertyValues(PREFIX + "access-token-lifetime=300000",
                PREFIX + "refresh-session-ttl=2592000", PREFIX + "refresh-cookie-secure=true",
                PREFIX + "refresh-cookie-same-site=Lax", PREFIX + "cleanup-enabled=true",
                PREFIX + "retention=7").run(context -> {
            assertThat(context).hasNotFailed();
            AuthSessionProperties config = context.getBean(AuthSessionProperties.class);
            assertThat(config.accessTokenLifetime()).isEqualTo(Duration.ofMillis(300000));
            assertThat(config.refreshSessionTtl()).isEqualTo(Duration.ofSeconds(2592000));
            assertThat(config.retention()).isEqualTo(Duration.ofDays(7));
        });
    }

    @Test
    void approvedLocalDevelopmentConfiguration() {
        runner.withPropertyValues(PREFIX + "access-token-lifetime=3600000",
                PREFIX + "refresh-cookie-secure=false", PREFIX + "cleanup-enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            AuthSessionProperties config = context.getBean(AuthSessionProperties.class);
            assertThat(config.refreshCookieSecure()).isFalse();
            assertThat(config.refreshCookieName()).isEqualTo("fintrack_refresh");
            assertThat(config.cleanupEnabled()).isFalse();
        });
    }

    @Test
    void productionProfilePinsSecureCookies() throws Exception {
        Properties prod = PropertiesLoaderUtils.loadProperties(
                new ClassPathResource("application-prod.properties"));
        assertThat(prod.getProperty(PREFIX + "refresh-cookie-secure")).isEqualTo("true");

        Properties base = PropertiesLoaderUtils.loadProperties(
                new ClassPathResource("application.properties"));
        assertThat(base.getProperty(PREFIX + "refresh-cookie-secure"))
                .isEqualTo("${REFRESH_COOKIE_SECURE:true}");
        assertThat(base.getProperty(PREFIX + "access-token-lifetime"))
                .isEqualTo("${app.jwt.expiration-ms}");
        assertThat(base.stringPropertyNames()).noneMatch(name -> name.contains("legacy")
                || name.contains("pepper") || name.startsWith(PREFIX + "refresh-token-secret")
                || name.startsWith(PREFIX + "refresh-cookie-name")
                || name.startsWith(PREFIX + "refresh-cookie-domain")
                || name.startsWith(PREFIX + "refresh-cookie-path"));
    }

    @ParameterizedTest
    @CsvSource({"Lax,LAX", "lax,LAX", "Strict,STRICT", "strict,STRICT"})
    void acceptsSupportedSameSiteValues(String value, SameSite expected) {
        runner.withPropertyValues(PREFIX + "refresh-cookie-same-site=" + value).run(context ->
                assertThat(context.getBean(AuthSessionProperties.class).refreshCookieSameSite())
                        .isEqualTo(expected));
    }

    @ParameterizedTest
    @ValueSource(strings = {"None", "none", "Lax;Secure", "Relaxed"})
    void rejectsUnsupportedSameSiteValues(String value) {
        runner.withPropertyValues(PREFIX + "refresh-cookie-same-site=" + value)
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"60000", "300000", "3600000"})
    void acceptsAccessLifetimeBoundaries(String millis) {
        runner.withPropertyValues(PREFIX + "access-token-lifetime=" + millis)
                .run(context -> assertThat(context).hasNotFailed());
    }

    @ParameterizedTest
    @CsvSource({
            "access-token-lifetime=0, access-token-lifetime (JWT_EXPIRATION_MS)",
            "access-token-lifetime=-300000, access-token-lifetime (JWT_EXPIRATION_MS)",
            "access-token-lifetime=59999, access-token-lifetime (JWT_EXPIRATION_MS)",
            "access-token-lifetime=3600001, access-token-lifetime (JWT_EXPIRATION_MS)",
            "refresh-session-ttl=0, refresh-session-ttl (REFRESH_SESSION_TTL_SECONDS)",
            "refresh-session-ttl=-1, refresh-session-ttl (REFRESH_SESSION_TTL_SECONDS)",
            "refresh-session-ttl=2592001, refresh-session-ttl (REFRESH_SESSION_TTL_SECONDS)",
            "retention=0, retention (REFRESH_SESSION_RETENTION_DAYS)",
            "retention=-7, retention (REFRESH_SESSION_RETENTION_DAYS)",
            "retention=31, retention (REFRESH_SESSION_RETENTION_DAYS)"
    })
    void rejectsOutOfBoundsValuesNamingTheProperty(String property, String expectedName) {
        runner.withPropertyValues(PREFIX + property).run(context -> {
            assertThat(context).hasFailed();
            assertThat(failure(context.getStartupFailure())).contains(expectedName);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"61", "86400", "2592000"})
    void acceptsRefreshLifetimeWithinBounds(String seconds) {
        runner.withPropertyValues(PREFIX + "access-token-lifetime=60000",
                PREFIX + "refresh-session-ttl=" + seconds)
                .run(context -> assertThat(context).hasNotFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "30"})
    void acceptsRetentionBoundaries(String days) {
        runner.withPropertyValues(PREFIX + "retention=" + days)
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void rejectsRefreshSessionThatDoesNotOutliveAccessToken() {
        runner.withPropertyValues(PREFIX + "access-token-lifetime=3600000",
                PREFIX + "refresh-session-ttl=3600").run(context -> {
            assertThat(context).hasFailed();
            assertThat(failure(context.getStartupFailure()))
                    .contains("must exceed app.auth-session.access-token-lifetime");
        });
    }

    @Test
    void blankValuesFallBackToApprovedSecureDefaults() {
        runner.withPropertyValues(PREFIX + "access-token-lifetime=", PREFIX + "refresh-session-ttl=",
                PREFIX + "refresh-cookie-same-site=",
                PREFIX + "retention=").run(context -> {
            assertThat(context).hasNotFailed();
            AuthSessionProperties config = context.getBean(AuthSessionProperties.class);
            assertThat(config.accessTokenLifetime()).isEqualTo(Duration.ofMinutes(5));
            assertThat(config.refreshSessionTtl()).isEqualTo(Duration.ofDays(30));
            assertThat(config.refreshCookieSameSite()).isEqualTo(SameSite.LAX);
            assertThat(config.retention()).isEqualTo(Duration.ofDays(7));
        });
    }

    @Test
    void blankSecureFlagFailsClosed() {
        runner.withPropertyValues(PREFIX + "refresh-cookie-secure=")
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"access-token-lifetime=five", "refresh-session-ttl=30d0",
            "refresh-cookie-secure=maybe", "retention=week"})
    void rejectsUnparseableValues(String property) {
        runner.withPropertyValues(PREFIX + property).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void validationFailureDoesNotEchoRejectedValue() {
        runner.withPropertyValues(PREFIX + "access-token-lifetime=987654321").run(context -> {
            assertThat(context).hasFailed();
            String text = failure(context.getStartupFailure());
            assertThat(text).contains("JWT_EXPIRATION_MS").doesNotContain("987654321");
        });
    }

    @Test
    void stringRepresentationIsRedacted() {
        runner.run(context -> assertThat(context.getBean(AuthSessionProperties.class).toString())
                .isEqualTo("AuthSessionProperties[configuration redacted]"));
    }
}
