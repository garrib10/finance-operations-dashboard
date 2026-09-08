package dev.portfolio.finance.dto.dashboard;

import java.math.BigDecimal;
import dev.portfolio.finance.entity.BudgetStatus;

public record BudgetSummaryResponse(
        Long budgetId,
        Long categoryId,
        String categoryName,
        BigDecimal monthlyLimit,
        BigDecimal amountSpent,
        BigDecimal amountRemaining,
        BigDecimal percentageUsed,
        BudgetStatus status
) {
}