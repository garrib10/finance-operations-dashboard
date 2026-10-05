package dev.portfolio.finance.service;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class ReportingPeriodProviderTest {

    private static ReportingPeriod at(String instant, String zone) {
        return new ReportingPeriodProvider(Clock.fixed(Instant.parse(instant), ZoneId.of(zone))).currentMonth();
    }

    @Test
    void coversTheWholeCalendarMonth() {
        ReportingPeriod period = at("2026-10-03T12:00:00Z", "UTC");

        assertThat(period).isEqualTo(new ReportingPeriod(2026, 10,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)));
    }

    @Test
    void handlesLeapYearsAndYearEnd() {
        assertThat(at("2028-02-10T00:00:00Z", "UTC").end()).isEqualTo(LocalDate.of(2028, 2, 29));
        assertThat(at("2027-02-10T00:00:00Z", "UTC").end()).isEqualTo(LocalDate.of(2027, 2, 28));
        assertThat(at("2026-12-31T23:59:59Z", "UTC").month()).isEqualTo(12);
    }

    @Test
    void usesTheClockZoneToDecideTheMonth() {
        // 03:30 UTC on 1 November is still 31 October in New York.
        ReportingPeriod newYork = at("2026-11-01T03:30:00Z", "America/New_York");
        ReportingPeriod utc = at("2026-11-01T03:30:00Z", "UTC");

        assertThat(newYork.month()).isEqualTo(10);
        assertThat(utc.month()).isEqualTo(11);
    }

    @Test
    void defaultsToTheSystemDefaultZoneLikeLocalDateNow() {
        ReportingPeriod period = new ReportingPeriodProvider().currentMonth();

        assertThat(period).isEqualTo(ReportingPeriod.monthOf(LocalDate.now()));
    }

    @Test
    void buildsAnyRequestedCalendarMonth() {
        assertThat(ReportingPeriod.of(2026, 8)).isEqualTo(new ReportingPeriod(2026, 8,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)));
        assertThat(ReportingPeriod.of(2026, 1)).isEqualTo(new ReportingPeriod(2026, 1,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)));
        assertThat(ReportingPeriod.of(2025, 12)).isEqualTo(new ReportingPeriod(2025, 12,
                LocalDate.of(2025, 12, 1), LocalDate.of(2025, 12, 31)));
        assertThat(ReportingPeriod.of(2027, 2).end()).isEqualTo(LocalDate.of(2027, 2, 28));
        assertThat(ReportingPeriod.of(2028, 2).end()).isEqualTo(LocalDate.of(2028, 2, 29));
        // The date-based factory gives the same month as the explicit one.
        assertThat(ReportingPeriod.monthOf(LocalDate.of(2026, 8, 17))).isEqualTo(ReportingPeriod.of(2026, 8));
    }

    @Test
    void usesTheCurrentMonthWithoutARequestAndTheRequestedMonthOtherwise() {
        ReportingPeriodProvider provider =
                new ReportingPeriodProvider(Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneId.of("UTC")));

        assertThat(provider.forMonth(null, null)).isEqualTo(provider.currentMonth());
        assertThat(provider.forMonth(8, 2026)).isEqualTo(ReportingPeriod.of(2026, 8));
        // The clock is not consulted for an explicit month.
        assertThat(provider.forMonth(2, 2028).end()).isEqualTo(LocalDate.of(2028, 2, 29));
    }
}
