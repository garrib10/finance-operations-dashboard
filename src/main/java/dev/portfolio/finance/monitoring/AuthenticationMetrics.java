package dev.portfolio.finance.monitoring;

import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Counts authentication failures (issue #20) as {@value #METRIC} with two fixed tags, so an
 * operator can see a spike in failed logins or rejected tokens. Tag values come only from the
 * enums below: never a user, email, token, cookie, address, path, or exception message.
 * Each failure is counted at one place only, so nothing is counted twice, and a metrics
 * problem can never change how authentication behaves.
 */
@Component
public class AuthenticationMetrics {

    public static final String METRIC = "fintrack.authentication.failures";

    /** Which credential failed. */
    public enum Flow { LOGIN, ACCESS_TOKEN, REFRESH_TOKEN }

    /**
     * Why it failed, as far as the application can tell for certain:
     * <ul>
     *   <li>{@code INVALID_CREDENTIALS}: login with a wrong email or password (never which one);</li>
     *   <li>{@code MISSING}: a protected request with no access token;</li>
     *   <li>{@code INVALID}: a token that is not recognized (bad signature, unknown, or for a
     *       deleted account);</li>
     *   <li>{@code EXPIRED}: a correctly signed or stored token past its lifetime;</li>
     *   <li>{@code MALFORMED}: a refresh cookie that is unreadable or sent twice;</li>
     *   <li>{@code REVOKED}: a refresh token from a family already ended (logout, password change);</li>
     *   <li>{@code REUSED}: an already-used refresh token, which ends its family.</li>
     * </ul>
     */
    public enum Reason { INVALID_CREDENTIALS, MISSING, INVALID, EXPIRED, MALFORMED, REVOKED, REUSED }

    private static final Logger log = LoggerFactory.getLogger(AuthenticationMetrics.class);

    private final MeterRegistry registry;

    public AuthenticationMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** Records one failure. Never throws: monitoring must not break sign-in. */
    public void failure(Flow flow, Reason reason) {
        try {
            Counter.builder(METRIC)
                    .description("Failed authentication attempts, by credential and reason")
                    .baseUnit("failures")
                    .tag("flow", tag(flow))
                    .tag("reason", tag(reason))
                    .register(registry)
                    .increment();
        } catch (RuntimeException ex) {
            log.warn("monitoring.metric_failed metric={} category={}", METRIC, ex.getClass().getSimpleName());
        }
    }

    private static String tag(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
