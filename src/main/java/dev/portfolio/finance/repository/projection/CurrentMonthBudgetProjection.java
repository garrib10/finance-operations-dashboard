package dev.portfolio.finance.repository.projection;

import java.math.BigDecimal;

/** A budget in the reporting month; at most one per category (unique key). */
public interface CurrentMonthBudgetProjection {

    Long getBudgetId();

    Long getCategoryId();

    BigDecimal getMonthlyLimit();
}
