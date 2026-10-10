package dev.portfolio.finance.dto.budget;

import java.util.List;

/**
 * Every budget the user has for one month, each with its analytics, in one response:
 * {@code month} and {@code year} echo the requested month, and {@code budgets} are ordered
 * by category name, then category ID. Empty when the month has no budgets.
 */
public record BudgetMonthAnalyticsResponse(
        int month,
        int year,
        List<BudgetAnalyticsResponse> budgets
) {
}
