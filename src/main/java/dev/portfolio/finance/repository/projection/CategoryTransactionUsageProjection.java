package dev.portfolio.finance.repository.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One user's transaction usage for one category, from a single grouped query. */
public interface CategoryTransactionUsageProjection {

    Long getCategoryId();

    /** Income and expense transactions. */
    Long getTransactionCount();

    LocalDate getLastTransactionDate();

    /** Expense amounts only. */
    BigDecimal getAllTimeSpent();

    /** Expense amounts in the reporting month only. */
    BigDecimal getCurrentMonthSpent();
}
