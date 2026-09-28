package dev.portfolio.finance.mysql;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import dev.portfolio.finance.support.MutableClock;

/**
 * Controllable UTC clock for the MySQL *IT context. Top-level (not nested in the base
 * class) so Spring's default-configuration detection never picks it up implicitly;
 * it is applied only through {@code @Import} on {@link MySqlIntegrationTestBase}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class MySqlTestClockConfig {

    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock(Instant.now().truncatedTo(ChronoUnit.SECONDS));
    }
}
