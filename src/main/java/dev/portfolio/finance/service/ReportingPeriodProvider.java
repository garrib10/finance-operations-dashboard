package dev.portfolio.finance.service;

import java.time.Clock;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The server's current reporting month, shared by the dashboard and category summary.
 * It uses the JVM default time zone (the same as {@code LocalDate.now()}) and keeps its
 * own clock rather than a {@code Clock} bean, because the UTC authentication clock is
 * injected by type and must stay the only one.
 */
@Component
public class ReportingPeriodProvider {

    private final Clock clock;

    @Autowired
    public ReportingPeriodProvider() {
        this(Clock.systemDefaultZone());
    }

    /** For tests: a fixed clock pins the reporting month. */
    public ReportingPeriodProvider(Clock clock) {
        this.clock = clock;
    }

    public ReportingPeriod currentMonth() {
        return ReportingPeriod.monthOf(LocalDate.now(clock));
    }

    /**
     * The current month when both values are absent, otherwise the requested month. Callers
     * validate the values first (the allowed range differs between features).
     */
    public ReportingPeriod forMonth(Integer month, Integer year) {
        if (month == null && year == null) {
            return currentMonth();
        }
        return ReportingPeriod.of(year, month);
    }
}
