package dev.portfolio.finance.repository.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One user's transaction usage for one category, from a single grouped query. */
public interface CategoryTransactionUsageProjection {

    Long getCategoryId();

    /** Income and expense transactions, all time. */
    Long getTransactionCount();

    /** Income and expense transactions in the reporting month (inclusive of both ends). */
    Long getCurrentMonthTransactionCount();

    LocalDate getLastTransactionDate();

    /** Expense amounts only. */
    BigDecimal getAllTimeSpent();

    /** Expense amounts in the reporting month only. */
    BigDecimal getCurrentMonthSpent();
}
