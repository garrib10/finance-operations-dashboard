package dev.portfolio.finance.config;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import dev.portfolio.finance.entity.RefreshSession;
import dev.portfolio.finance.entity.RefreshSessionRevocationReason;
import dev.portfolio.finance.entity.RefreshToken;
import dev.portfolio.finance.support.MutableClock;
import dev.portfolio.finance.support.TestDataFactory;

class AuthSessionConfigTest {

    private static final Instant FIXED = Instant.parse("2026-01-15T08:30:00Z");

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AuthSessionConfig.class);

    @Configuration(proxyBeanMethods = false)
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(FIXED, ZoneOffset.UTC);
        }
    }

    @Test
    void productionClockIsSystemUtc() {
        runner.run(context -> {
            Clock clock = context.getBean(Clock.class);
            assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
            assertThat(clock).isEqualTo(Clock.systemUTC());
            Instant before = Instant.now();
            assertThat(clock.instant()).isBetween(before, Instant.now());
        });
    }

    @Test
    void testsCanReplaceClockAndDriveDeterministicSessionTimes() {
        runner.withUserConfiguration(FixedClockConfig.class).run(context -> {
            Clock clock = context.getBean(Clock.class);
            Duration ttl = context.getBean(AuthSessionProperties.class).refreshSessionTtl();

            RefreshSession session = RefreshSession.start(TestDataFactory.createUser(), clock, ttl);

            assertThat(session.getCreatedAt()).isEqualTo(FIXED);
            assertThat(session.getExpiresAt()).isEqualTo(Instant.parse("2026-02-14T08:30:00Z"));
        });
    }

    @Test
    void mutableClockDrivesExpirationRevocationAndConsumption() {
        MutableClock clock = new MutableClock(FIXED);
        RefreshSession session = RefreshSession.start(TestDataFactory.createUser(), clock, Duration.ofDays(30));
        RefreshToken token = RefreshToken.issue(session, new byte[32], clock);

        clock.advance(Duration.ofMinutes(5));
        token.markConsumed(clock);
        clock.advance(Duration.ofDays(1));
        session.revoke(RefreshSessionRevocationReason.LOGOUT, clock);

        assertThat(token.getCreatedAt()).isEqualTo(FIXED);
        assertThat(token.getConsumedAt()).isEqualTo(Instant.parse("2026-01-15T08:35:00Z"));
        assertThat(session.getRevokedAt()).isEqualTo(Instant.parse("2026-01-16T08:35:00Z"));
        assertThat(session.getExpiresAt()).isEqualTo(Instant.parse("2026-02-14T08:30:00Z"));
    }
}
