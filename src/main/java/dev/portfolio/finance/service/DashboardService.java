package dev.portfolio.finance.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import dev.portfolio.finance.dto.budget.BudgetAnalyticsResponse;
import dev.portfolio.finance.dto.dashboard.BudgetSummaryResponse;
import dev.portfolio.finance.dto.dashboard.CategorySpendingResponse;
import dev.portfolio.finance.dto.dashboard.DashboardResponse;
import dev.portfolio.finance.dto.dashboard.RecentTransactionResponse;
import dev.portfolio.finance.entity.Transaction;
import dev.portfolio.finance.entity.TransactionType;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.repository.BudgetRepository;
import dev.portfolio.finance.repository.TransactionRepository;
import dev.portfolio.finance.repository.UserRepository;

@Service
public class DashboardService {

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;
    private final BudgetService budgetService;

    public DashboardService(
            UserRepository userRepository,
            TransactionRepository transactionRepository,
            BudgetRepository budgetRepository,
            BudgetService budgetService
    ) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.budgetRepository = budgetRepository;
        this.budgetService = budgetService;
    }

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(
            String authenticatedEmail
    ) {
        User user = userRepository
                .findByEmail(authenticatedEmail)
                .orElseThrow();

        BigDecimal totalIncome =
                transactionRepository.sumAmountByUserAndType(
                        user.getId(),
                        TransactionType.INCOME
                );

        BigDecimal totalExpenses =
                transactionRepository.sumAmountByUserAndType(
                        user.getId(),
                        TransactionType.EXPENSE
                );

        BigDecimal currentBalance =
                totalIncome.subtract(totalExpenses);

        LocalDate today = LocalDate.now();

        LocalDate startDate = today.withDayOfMonth(1);

        LocalDate endDate = startDate.withDayOfMonth(
                startDate.lengthOfMonth()
        );

        BigDecimal monthlyIncome =
                transactionRepository.sumAmountByUserTypeAndDateRange(
                        user.getId(),
                        TransactionType.INCOME,
                        startDate,
                        endDate
                );

        BigDecimal monthlyExpenses =
                transactionRepository.sumAmountByUserTypeAndDateRange(
                        user.getId(),
                        TransactionType.EXPENSE,
                        startDate,
                        endDate
                );

        List<RecentTransactionResponse> recentTransactions =
                transactionRepository
                        .findTop5ByUserIdOrderByTransactionDateDescCreatedAtDesc(
                                user.getId()
                        )
                        .stream()
                        .map(this::mapRecentTransaction)
                        .toList();

        List<CategorySpendingResponse> categorySpending =
                transactionRepository
                        .findSpendingByCategory(
                                user.getId(),
                                TransactionType.EXPENSE,
                                startDate,
                                endDate
                        )
                        .stream()
                        .map(projection ->
                                new CategorySpendingResponse(
                                        projection.getCategoryId(),
                                        projection.getCategoryName(),
                                        projection.getAmountSpent()
                                )
                        )
                        .toList();

        List<BudgetSummaryResponse> budgetSummaries =
                budgetRepository
                        .findAllByUserIdOrderByYearDescMonthDesc(
                                user.getId()
                        )
                        .stream()
                        .filter(budget ->
                                budget.getMonth() == today.getMonthValue()
                                        && budget.getYear() == today.getYear()
                        )
                        .map(budget -> {
                            BudgetAnalyticsResponse analytics =
                                    budgetService.getBudgetAnalytics(
                                            authenticatedEmail,
                                            budget.getId()
                                    );

                            return new BudgetSummaryResponse(
                                    analytics.budgetId(),
                                    analytics.categoryId(),
                                    analytics.categoryName(),
                                    analytics.monthlyLimit(),
                                    analytics.amountSpent(),
                                    analytics.amountRemaining(),
                                    analytics.percentageUsed(),
                                    analytics.status()
                            );
                        })
                        .toList();

        return new DashboardResponse(
                currentBalance,
                totalIncome,
                totalExpenses,
                monthlyIncome,
                monthlyExpenses,
                recentTransactions,
                budgetSummaries,
                categorySpending
        );
    }

    private RecentTransactionResponse mapRecentTransaction(
            Transaction transaction
    ) {
        return new RecentTransactionResponse(
                transaction.getId(),
                transaction.getCategory().getId(),
                transaction.getCategory().getName(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getDescription(),
                transaction.getTransactionDate()
        );
    }
}