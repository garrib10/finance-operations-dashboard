package dev.portfolio.finance.dto.category;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One category's usage. Money values have scale 2 and are never null; spending counts
 * expenses only, while {@code transactionCount} includes income.
 *
 * @param lastTransactionDate null when the category has never been used
 * @param currentMonthBudget null when there is no budget for the reporting month
 * @param canDelete whether the API would accept a delete now: custom and unused.
 *        The server still re-checks on delete, so a later reference is refused.
 */
public record CategorySummaryResponse(
        Long id,
        String name,
        String iconKey,
        boolean builtIn,
        boolean budgetEnabled,
        long transactionCount,
        long budgetCount,
        LocalDate lastTransactionDate,
        BigDecimal currentMonthSpent,
        BigDecimal allTimeSpent,
        CurrentMonthBudgetResponse currentMonthBudget,
        boolean canDelete
) {
}
