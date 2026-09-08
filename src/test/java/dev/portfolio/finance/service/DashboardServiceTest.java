package dev.portfolio.finance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.portfolio.finance.dto.budget.BudgetAnalyticsResponse;
import dev.portfolio.finance.dto.dashboard.DashboardResponse;
import dev.portfolio.finance.entity.Budget;
import dev.portfolio.finance.entity.BudgetStatus;
import dev.portfolio.finance.entity.Category;
import dev.portfolio.finance.entity.Transaction;
import dev.portfolio.finance.entity.TransactionType;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.repository.BudgetRepository;
import dev.portfolio.finance.repository.TransactionRepository;
import dev.portfolio.finance.repository.UserRepository;
import dev.portfolio.finance.repository.projection.CategorySpendingProjection;
import dev.portfolio.finance.support.TestDataFactory;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final String TEST_EMAIL =
            "test@example.com";

    @Mock
    private UserRepository userRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private BudgetRepository budgetRepository;

    @Mock
    private BudgetService budgetService;

    @InjectMocks
    private DashboardService dashboardService;

    @Test
    void shouldReturnCompleteDashboardForAuthenticatedUser() {
        // Arrange
        User user =
                TestDataFactory.createUser();

        Category groceries =
                TestDataFactory.createCategory(
                        user,
                        "Groceries"
                );

        Transaction recentExpense =
                TestDataFactory.createTransaction(
                        user,
                        groceries,
                        TransactionType.EXPENSE,
                        new BigDecimal("125.50"),
                        "Weekly groceries",
                        LocalDate.now()
                );

        BigDecimal totalIncome =
                new BigDecimal("5000.00");

        BigDecimal totalExpenses =
                new BigDecimal("1850.00");

        BigDecimal monthlyIncome =
                new BigDecimal("3000.00");

        BigDecimal monthlyExpenses =
                new BigDecimal("950.00");

        LocalDate today =
                LocalDate.now();

        LocalDate startDate =
                today.withDayOfMonth(1);

        LocalDate endDate =
                startDate.withDayOfMonth(
                        startDate.lengthOfMonth()
                );

        CategorySpendingProjection spendingProjection =
                new CategorySpendingProjection() {

                    @Override
                    public Long getCategoryId() {
                        return groceries.getId();
                    }

                    @Override
                    public String getCategoryName() {
                        return groceries.getName();
                    }

                    @Override
                    public BigDecimal getAmountSpent() {
                        return new BigDecimal("425.00");
                    }
                };

        Budget currentBudget =
                TestDataFactory.createBudget(
                        user,
                        groceries,
                        new BigDecimal("700.00"),
                        today.getMonthValue(),
                        today.getYear()
                );

        BudgetAnalyticsResponse analytics =
                new BudgetAnalyticsResponse(
                        currentBudget.getId(),
                        groceries.getId(),
                        groceries.getName(),
                        new BigDecimal("700.00"),
                        new BigDecimal("425.00"),
                        new BigDecimal("275.00"),
                        new BigDecimal("60.71"),
                        BudgetStatus.CAUTION,
                        today.getMonthValue(),
                        today.getYear()
                );

        when(userRepository.findByEmail(TEST_EMAIL))
                .thenReturn(Optional.of(user));

        when(transactionRepository.sumAmountByUserAndType(
                user.getId(),
                TransactionType.INCOME
        )).thenReturn(totalIncome);

        when(transactionRepository.sumAmountByUserAndType(
                user.getId(),
                TransactionType.EXPENSE
        )).thenReturn(totalExpenses);

        when(transactionRepository.sumAmountByUserTypeAndDateRange(
                user.getId(),
                TransactionType.INCOME,
                startDate,
                endDate
        )).thenReturn(monthlyIncome);

        when(transactionRepository.sumAmountByUserTypeAndDateRange(
                user.getId(),
                TransactionType.EXPENSE,
                startDate,
                endDate
        )).thenReturn(monthlyExpenses);

        when(transactionRepository
                .findTop5ByUserIdOrderByTransactionDateDescCreatedAtDesc(
                        user.getId()
                ))
                .thenReturn(List.of(recentExpense));

        when(transactionRepository.findSpendingByCategory(
                user.getId(),
                TransactionType.EXPENSE,
                startDate,
                endDate
        )).thenReturn(List.of(spendingProjection));

        when(budgetRepository
                .findAllByUserIdOrderByYearDescMonthDesc(
                        user.getId()
                ))
                .thenReturn(List.of(currentBudget));

        when(budgetService.getBudgetAnalytics(
                TEST_EMAIL,
                currentBudget.getId()
        )).thenReturn(analytics);

        // Act
        DashboardResponse response =
                dashboardService.getDashboard(
                        TEST_EMAIL
                );

        // Assert
        assertEquals(
                new BigDecimal("3150.00"),
                response.currentBalance()
        );

        assertEquals(
                totalIncome,
                response.totalIncome()
        );

        assertEquals(
                totalExpenses,
                response.totalExpenses()
        );

        assertEquals(
                monthlyIncome,
                response.monthlyIncome()
        );

        assertEquals(
                monthlyExpenses,
                response.monthlyExpenses()
        );

        assertEquals(
                1,
                response.recentTransactions().size()
        );

        assertEquals(
                "Groceries",
                response.recentTransactions()
                        .get(0)
                        .categoryName()
        );

        assertEquals(
                new BigDecimal("125.50"),
                response.recentTransactions()
                        .get(0)
                        .amount()
        );

        assertEquals(
                1,
                response.categorySpending().size()
        );

        assertEquals(
                new BigDecimal("425.00"),
                response.categorySpending()
                        .get(0)
                        .amountSpent()
        );

        assertEquals(
                1,
                response.budgetSummaries().size()
        );

        assertEquals(
                BudgetStatus.CAUTION,
                response.budgetSummaries()
                        .get(0)
                        .status()
        );

        verify(budgetService)
                .getBudgetAnalytics(
                        TEST_EMAIL,
                        currentBudget.getId()
                );
    }

    @Test
    void shouldExcludeBudgetsOutsideCurrentMonthFromDashboard() {
        // Arrange
        User user =
                TestDataFactory.createUser();

        Category category =
                TestDataFactory.createCategory(
                        user,
                        "Groceries"
                );

        LocalDate today =
                LocalDate.now();

        LocalDate startDate =
                today.withDayOfMonth(1);

        LocalDate endDate =
                startDate.withDayOfMonth(
                        startDate.lengthOfMonth()
                );

        int previousMonth =
                today.minusMonths(1)
                        .getMonthValue();

        int previousMonthYear =
                today.minusMonths(1)
                        .getYear();

        Budget previousBudget =
                TestDataFactory.createBudget(
                        user,
                        category,
                        new BigDecimal("700.00"),
                        previousMonth,
                        previousMonthYear
                );

        when(userRepository.findByEmail(TEST_EMAIL))
                .thenReturn(Optional.of(user));

        when(transactionRepository.sumAmountByUserAndType(
                user.getId(),
                TransactionType.INCOME
        )).thenReturn(BigDecimal.ZERO);

        when(transactionRepository.sumAmountByUserAndType(
                user.getId(),
                TransactionType.EXPENSE
        )).thenReturn(BigDecimal.ZERO);

        when(transactionRepository.sumAmountByUserTypeAndDateRange(
                user.getId(),
                TransactionType.INCOME,
                startDate,
                endDate
        )).thenReturn(BigDecimal.ZERO);

        when(transactionRepository.sumAmountByUserTypeAndDateRange(
                user.getId(),
                TransactionType.EXPENSE,
                startDate,
                endDate
        )).thenReturn(BigDecimal.ZERO);

        when(transactionRepository
                .findTop5ByUserIdOrderByTransactionDateDescCreatedAtDesc(
                        user.getId()
                ))
                .thenReturn(List.of());

        when(transactionRepository.findSpendingByCategory(
                user.getId(),
                TransactionType.EXPENSE,
                startDate,
                endDate
        )).thenReturn(List.of());

        when(budgetRepository
                .findAllByUserIdOrderByYearDescMonthDesc(
                        user.getId()
                ))
                .thenReturn(List.of(previousBudget));

        // Act
        DashboardResponse response =
                dashboardService.getDashboard(
                        TEST_EMAIL
                );

        // Assert
        assertEquals(
                0,
                response.budgetSummaries().size()
        );

        verify(
                budgetService,
                never()
        ).getBudgetAnalytics(
                TEST_EMAIL,
                previousBudget.getId()
        );
    }

    @Test
    void shouldReturnEmptyDashboardCollectionsWhenUserHasNoActivity() {
        // Arrange
        User user =
                TestDataFactory.createUser();

        LocalDate today =
                LocalDate.now();

        LocalDate startDate =
                today.withDayOfMonth(1);

        LocalDate endDate =
                startDate.withDayOfMonth(
                        startDate.lengthOfMonth()
                );

        when(userRepository.findByEmail(TEST_EMAIL))
                .thenReturn(Optional.of(user));

        when(transactionRepository.sumAmountByUserAndType(
                user.getId(),
                TransactionType.INCOME
        )).thenReturn(BigDecimal.ZERO);

        when(transactionRepository.sumAmountByUserAndType(
                user.getId(),
                TransactionType.EXPENSE
        )).thenReturn(BigDecimal.ZERO);

        when(transactionRepository.sumAmountByUserTypeAndDateRange(
                user.getId(),
                TransactionType.INCOME,
                startDate,
                endDate
        )).thenReturn(BigDecimal.ZERO);

        when(transactionRepository.sumAmountByUserTypeAndDateRange(
                user.getId(),
                TransactionType.EXPENSE,
                startDate,
                endDate
        )).thenReturn(BigDecimal.ZERO);

        when(transactionRepository
                .findTop5ByUserIdOrderByTransactionDateDescCreatedAtDesc(
                        user.getId()
                ))
                .thenReturn(List.of());

        when(transactionRepository.findSpendingByCategory(
                user.getId(),
                TransactionType.EXPENSE,
                startDate,
                endDate
        )).thenReturn(List.of());

        when(budgetRepository
                .findAllByUserIdOrderByYearDescMonthDesc(
                        user.getId()
                ))
                .thenReturn(List.of());

        // Act
        DashboardResponse response =
                dashboardService.getDashboard(
                        TEST_EMAIL
                );

        // Assert
        assertEquals(
                BigDecimal.ZERO,
                response.currentBalance()
        );

        assertEquals(
                BigDecimal.ZERO,
                response.totalIncome()
        );

        assertEquals(
                BigDecimal.ZERO,
                response.totalExpenses()
        );

        assertEquals(
                BigDecimal.ZERO,
                response.monthlyIncome()
        );

        assertEquals(
                BigDecimal.ZERO,
                response.monthlyExpenses()
        );

        assertEquals(
                0,
                response.recentTransactions().size()
        );

        assertEquals(
                0,
                response.categorySpending().size()
        );

        assertEquals(
                0,
                response.budgetSummaries().size()
        );
    }
}