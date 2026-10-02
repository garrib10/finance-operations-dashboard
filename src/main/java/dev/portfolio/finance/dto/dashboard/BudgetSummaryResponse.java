package dev.portfolio.finance.dto.dashboard;

import java.math.BigDecimal;
import dev.portfolio.finance.entity.BudgetStatus;

public record BudgetSummaryResponse(
        Long budgetId,
        Long categoryId,
        String categoryName,
        /** Approved icon key of the category; "tag" when the stored key is unknown. */
        String categoryIconKey,
        BigDecimal monthlyLimit,
        BigDecimal amountSpent,
        BigDecimal amountRemaining,
        BigDecimal percentageUsed,
        BudgetStatus status
) {
}