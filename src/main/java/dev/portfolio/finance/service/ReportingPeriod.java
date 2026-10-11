package dev.portfolio.finance.service;

import java.time.LocalDate;
import java.time.YearMonth;

/** A whole calendar month, inclusive of both ends; future-dated days in it count. */
public record ReportingPeriod(int year, int month, LocalDate start, LocalDate end) {

    /** The given calendar month; YearMonth handles month lengths, leap years, and year ends. */
    public static ReportingPeriod of(int year, int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        return new ReportingPeriod(year, month, yearMonth.atDay(1), yearMonth.atEndOfMonth());
    }

    public static ReportingPeriod monthOf(LocalDate date) {
        return of(date.getYear(), date.getMonthValue());
    }
}
