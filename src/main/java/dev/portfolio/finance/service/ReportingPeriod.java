package dev.portfolio.finance.service;

import java.time.LocalDate;

/** A whole calendar month, inclusive of both ends; future-dated days in it count. */
public record ReportingPeriod(int year, int month, LocalDate start, LocalDate end) {

    public static ReportingPeriod monthOf(LocalDate date) {
        LocalDate start = date.withDayOfMonth(1);
        return new ReportingPeriod(
                start.getYear(),
                start.getMonthValue(),
                start,
                start.withDayOfMonth(start.lengthOfMonth())
        );
    }
}
