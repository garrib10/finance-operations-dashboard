package dev.portfolio.finance.dto.category;

import java.math.BigDecimal;
import dev.portfolio.finance.entity.BudgetStatus;

/** The category's budget for the reporting month, with the same metrics as budget analytics. */
public record CurrentMonthBudgetResponse(
        Long budgetId,
        BigDecimal monthlyLimit,
        BigDecimal amountSpent,
        BigDecimal amountRemaining,
        BigDecimal percentageUsed,
        BudgetStatus status
) {
}
