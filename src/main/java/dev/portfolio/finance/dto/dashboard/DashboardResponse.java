package dev.portfolio.finance.dto.dashboard;

import java.math.BigDecimal;
import java.util.List;

public record DashboardResponse(
        BigDecimal currentBalance,
        BigDecimal totalIncome,
        BigDecimal totalExpenses,
        BigDecimal monthlyIncome,
        BigDecimal monthlyExpenses,
        List<RecentTransactionResponse> recentTransactions,
        List<BudgetSummaryResponse> budgetSummaries,
        List<CategorySpendingResponse> categorySpending
) {
}