package dev.portfolio.finance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import dev.portfolio.finance.dto.budget.BudgetAnalyticsResponse;
import dev.portfolio.finance.entity.Budget;
import dev.portfolio.finance.entity.BudgetStatus;
import dev.portfolio.finance.entity.Category;
import dev.portfolio.finance.entity.TransactionType;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.repository.BudgetRepository;
import dev.portfolio.finance.repository.CategoryRepository;
import dev.portfolio.finance.repository.TransactionRepository;
import dev.portfolio.finance.repository.UserRepository;
import dev.portfolio.finance.support.TestDataFactory;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    private static final String TEST_EMAIL = "test@example.com";
    private static final Long BUDGET_ID = 1L;

    @Mock
    private BudgetRepository budgetRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private BudgetService budgetService;

    @Test
    void shouldReturnOverBudgetAnalyticsWhenSpendingExceedsLimit() {
        // Arrange
        User user = TestDataFactory.createUser();

        Category category =
                TestDataFactory.createCategory(
                        user,
                        "Groceries"
                );

        Budget budget =
                TestDataFactory.createBudget(
                        user,
                        category,
                        new BigDecimal("700.00"),
                        8,
                        2026
                );

        BigDecimal amountSpent =
                new BigDecimal("735.00");

        LocalDate startDate =
                LocalDate.of(2026, 8, 1);

        LocalDate endDate =
                LocalDate.of(2026, 8, 31);

        when(userRepository.findByEmail(TEST_EMAIL))
                .thenReturn(Optional.of(user));

        when(budgetRepository.findByIdAndUserId(
                BUDGET_ID,
                user.getId()
        )).thenReturn(Optional.of(budget));

        when(transactionRepository
                .sumAmountByUserCategoryTypeAndDateRange(
                        user.getId(),
                        category.getId(),
                        TransactionType.EXPENSE,
                        startDate,
                        endDate
                ))
                .thenReturn(amountSpent);

        // Act
        BudgetAnalyticsResponse response =
                budgetService.getBudgetAnalytics(
                        TEST_EMAIL,
                        BUDGET_ID
                );

        // Assert
        assertEquals(
                new BigDecimal("700.00"),
                response.monthlyLimit()
        );

        assertEquals(
                new BigDecimal("735.00"),
                response.amountSpent()
        );

        assertEquals(
                new BigDecimal("-35.00"),
                response.amountRemaining()
        );

        assertEquals(
                new BigDecimal("105.00"),
                response.percentageUsed()
        );

        assertEquals(
                BudgetStatus.OVER_BUDGET,
                response.status()
        );

        assertEquals(8, response.month());
        assertEquals(2026, response.year());

        verify(userRepository)
                .findByEmail(TEST_EMAIL);

        verify(budgetRepository)
                .findByIdAndUserId(
                        BUDGET_ID,
                        user.getId()
                );

        verify(transactionRepository)
                .sumAmountByUserCategoryTypeAndDateRange(
                        user.getId(),
                        category.getId(),
                        TransactionType.EXPENSE,
                        startDate,
                        endDate
                );
    }
}