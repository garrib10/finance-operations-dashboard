package dev.portfolio.finance.dto.budget;

import java.math.BigDecimal;

import dev.portfolio.finance.entity.BudgetStatus;

public record BudgetAnalyticsResponse(
        Long budgetId,
        Long categoryId,
        String categoryName,
        BigDecimal monthlyLimit,
        BigDecimal amountSpent,
        BigDecimal amountRemaining,
        BigDecimal percentageUsed,
        BudgetStatus status,
        int month,
        int year
) {
}