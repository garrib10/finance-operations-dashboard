package dev.portfolio.finance.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthSessionProperties.class)
public class AuthSessionConfig {

    /** Refresh-session time source; tests replace it with a fixed or mutable clock. */
    @Bean
    public Clock authSessionClock() {
        return Clock.systemUTC();
    }
}
