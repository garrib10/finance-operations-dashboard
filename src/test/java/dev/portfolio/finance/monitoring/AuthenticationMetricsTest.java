package dev.portfolio.finance.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import dev.portfolio.finance.monitoring.AuthenticationMetrics.Flow;
import dev.portfolio.finance.monitoring.AuthenticationMetrics.Reason;

class AuthenticationMetricsTest {

    @Test
    void countsByFixedLowercaseFlowAndReason() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AuthenticationMetrics metrics = new AuthenticationMetrics(registry);

        metrics.failure(Flow.REFRESH_TOKEN, Reason.REUSED);
        metrics.failure(Flow.REFRESH_TOKEN, Reason.REUSED);
        metrics.failure(Flow.LOGIN, Reason.INVALID_CREDENTIALS);

        assertThat(registry.get(AuthenticationMetrics.METRIC).tags("flow", "refresh_token", "reason", "reused")
                .counter().count()).isEqualTo(2);
        assertThat(registry.get(AuthenticationMetrics.METRIC).tags("flow", "login", "reason", "invalid_credentials")
                .counter().count()).isEqualTo(1);
        assertThat(registry.get(AuthenticationMetrics.METRIC).counter().getId().getBaseUnit()).isEqualTo("failures");
    }

    @Test
    void neverLetsAMetricsProblemBreakAuthentication() {
        MeterRegistry broken = mock(MeterRegistry.class);
        when(broken.config()).thenThrow(new IllegalStateException("registry down"));
        when(broken.counter(any(), any(Iterable.class))).thenThrow(new IllegalStateException("registry down"));

        assertThatCode(() -> new AuthenticationMetrics(broken).failure(Flow.LOGIN, Reason.INVALID_CREDENTIALS))
                .doesNotThrowAnyException();
    }
}
