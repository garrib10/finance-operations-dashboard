package dev.portfolio.finance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import dev.portfolio.finance.dto.budget.BudgetAnalyticsResponse;
import dev.portfolio.finance.dto.budget.BudgetResponse;
import dev.portfolio.finance.dto.budget.CreateBudgetRequest;
import dev.portfolio.finance.dto.budget.UpdateBudgetRequest;
import dev.portfolio.finance.entity.Budget;
import dev.portfolio.finance.entity.BudgetStatus;
import dev.portfolio.finance.entity.Category;
import dev.portfolio.finance.entity.TransactionType;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.exception.budget.BudgetNotFoundException;
import dev.portfolio.finance.exception.budget.DuplicateBudgetException;
import dev.portfolio.finance.exception.category.CategoryNotFoundException;
import dev.portfolio.finance.repository.BudgetRepository;
import dev.portfolio.finance.repository.CategoryRepository;
import dev.portfolio.finance.repository.TransactionRepository;
import dev.portfolio.finance.repository.UserRepository;
import dev.portfolio.finance.support.TestDataFactory;


@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    private static final String TEST_EMAIL =
            "test@example.com";

    private static final Long BUDGET_ID =
            1L;

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

    // ---------------------------------------------------------
    // CREATE BUDGET
    // ---------------------------------------------------------

    @Test
    void shouldCreateBudgetForAuthenticatedUser() {
        // Arrange
        User user =
                TestDataFactory.createUser();

        Category category =
                TestDataFactory.createCategory(
                        user,
                        "Groceries"
                );

        CreateBudgetRequest request =
                new CreateBudgetRequest(
                        1L,
                        new BigDecimal("700.00"),
                        9,
                        2026
                );

        when(userRepository.findByEmail(TEST_EMAIL))
                .thenReturn(Optional.of(user));

        when(categoryRepository.findByIdAndUserId(
                request.categoryId(),
                user.getId()
        )).thenReturn(Optional.of(category));

        when(budgetRepository
                .existsByUserIdAndCategoryIdAndMonthAndYear(
                        user.getId(),
                        category.getId(),
                        request.month(),
                        request.year()
                ))
                .thenReturn(false);

        when(budgetRepository.save(
                any(Budget.class)
        )).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        // Act
        BudgetResponse response =
                budgetService.createBudget(
                        TEST_EMAIL,
                        request
                );

        // Assert
        assertEquals(
                "Groceries",
                response.categoryName()
        );

        assertEquals(
                new BigDecimal("700.00"),
                response.monthlyLimit()
        );

        assertEquals(
                9,
                response.month()
        );

        assertEquals(
                2026,
                response.year()
        );

        verify(budgetRepository)
                .save(any(Budget.class));
    }

    @Test
    void shouldThrowDuplicateBudgetExceptionWhenBudgetAlreadyExists() {
        // Arrange
        User user =
                TestDataFactory.createUser();

        Category category =
                TestDataFactory.createCategory(
                        user,
                        "Groceries"
                );

        CreateBudgetRequest request =
                new CreateBudgetRequest(
                        1L,
                        new BigDecimal("700.00"),
                        9,
                        2026
                );

        when(userRepository.findByEmail(TEST_EMAIL))
                .thenReturn(Optional.of(user));

        when(categoryRepository.findByIdAndUserId(
                request.categoryId(),
                user.getId()
        )).thenReturn(Optional.of(category));

        when(budgetRepository
                .existsByUserIdAndCategoryIdAndMonthAndYear(
                        user.getId(),
                        category.getId(),
                        request.month(),
                        request.year()
                ))
                .thenReturn(true);

        // Act + Assert
        DuplicateBudgetException exception =
                assertThrows(
                        DuplicateBudgetException.class,
                        () -> budgetService.createBudget(
                                TEST_EMAIL,
                                request
                        )
                );

        assertEquals(
                "Budget already exists for this category and month",
                exception.getMessage()
        );

        verify(budgetRepository, never())
                .save(any(Budget.class));
    }

    // ---------------------------------------------------------
    // GET BUDGET
    // ---------------------------------------------------------

    @Test
    void shouldReturnBudgetWhenOwnedByAuthenticatedUser() {
        // Arrange
        User user =
                TestDataFactory.createUser();

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
                        9,
                        2026
                );

        when(userRepository.findByEmail(TEST_EMAIL))
                .thenReturn(Optional.of(user));

        when(budgetRepository.findByIdAndUserId(
                BUDGET_ID,
                user.getId()
        )).thenReturn(Optional.of(budget));

        // Act
        BudgetResponse response =
                budgetService.getBudgetById(
                        TEST_EMAIL,
                        BUDGET_ID
                );

        // Assert
        assertEquals(
                "Groceries",
                response.categoryName()
        );

        assertEquals(
                new BigDecimal("700.00"),
                response.monthlyLimit()
        );

        assertEquals(
                9,
                response.month()
        );

        assertEquals(
                2026,
                response.year()
        );

        verify(budgetRepository)
                .findByIdAndUserId(
                        BUDGET_ID,
                        user.getId()
                );
    }

    @Test
    void shouldThrowBudgetNotFoundWhenBudgetIsNotOwnedByAuthenticatedUser() {
        // Arrange
        User user =
                TestDataFactory.createUser();

        when(userRepository.findByEmail(TEST_EMAIL))
                .thenReturn(Optional.of(user));

        when(budgetRepository.findByIdAndUserId(
                BUDGET_ID,
                user.getId()
        )).thenReturn(Optional.empty());

        // Act + Assert
        assertThrows(
                BudgetNotFoundException.class,
                () -> budgetService.getBudgetById(
                        TEST_EMAIL,
                        BUDGET_ID
                )
        );

        verify(budgetRepository)
                .findByIdAndUserId(
                        BUDGET_ID,
                        user.getId()
                );
    }

    // ---------------------------------------------------------
    // UPDATE BUDGET
    // ---------------------------------------------------------

    @Test
    void shouldThrowDuplicateBudgetExceptionWhenUpdateCreatesDuplicateIdentity() {
        // Arrange
        User user =
                TestDataFactory.createUser();

        Budget budget =
                mock(Budget.class);

        Category originalCategory =
                mock(Category.class);

        Category updatedCategory =
                mock(Category.class);

        UpdateBudgetRequest request =
                new UpdateBudgetRequest(
                        2L,
                        new BigDecimal("900.00"),
                        10,
                        2026
                );

        when(userRepository.findByEmail(TEST_EMAIL))
                .thenReturn(Optional.of(user));

        when(budgetRepository.findByIdAndUserId(
                BUDGET_ID,
                user.getId()
        )).thenReturn(Optional.of(budget));

        when(categoryRepository.findByIdAndUserId(
                request.categoryId(),
                user.getId()
        )).thenReturn(Optional.of(updatedCategory));

        when(budget.getCategory())
                .thenReturn(originalCategory);

        when(originalCategory.getId())
                .thenReturn(1L);

        when(updatedCategory.getId())
                .thenReturn(2L);

        when(budgetRepository
                .existsByUserIdAndCategoryIdAndMonthAndYear(
                        user.getId(),
                        updatedCategory.getId(),
                        request.month(),
                        request.year()
                ))
                .thenReturn(true);

        // Act + Assert
        DuplicateBudgetException exception =
                assertThrows(
                        DuplicateBudgetException.class,
                        () -> budgetService.updateBudget(
                                TEST_EMAIL,
                                BUDGET_ID,
                                request
                        )
                );

        assertEquals(
                "Budget already exists for this category and month",
                exception.getMessage()
        );

        verify(budgetRepository, never())
                .save(any(Budget.class));
    }

    // ---------------------------------------------------------
    // DELETE BUDGET
    // ---------------------------------------------------------

    @Test
    void shouldDeleteBudgetWhenOwnedByAuthenticatedUser() {
        // Arrange
        User user =
                TestDataFactory.createUser();

        Category category =
                TestDataFactory.createCategory(user);

        Budget budget =
                TestDataFactory.createBudget(
                        user,
                        category
                );

        when(userRepository.findByEmail(TEST_EMAIL))
                .thenReturn(Optional.of(user));

        when(budgetRepository.findByIdAndUserId(
                BUDGET_ID,
                user.getId()
        )).thenReturn(Optional.of(budget));

        // Act
        budgetService.deleteBudget(
                TEST_EMAIL,
                BUDGET_ID
        );

        // Assert
        verify(budgetRepository)
                .delete(budget);
    }

    // ---------------------------------------------------------
    // BUDGET ANALYTICS
    // ---------------------------------------------------------

    @Test
    void shouldReturnOverBudgetAnalyticsWhenSpendingExceedsLimit() {
        // Arrange
        User user =
                TestDataFactory.createUser();

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

        assertEquals(
                8,
                response.month()
        );

        assertEquals(
                2026,
                response.year()
        );
    }

    @ParameterizedTest
    @CsvSource({
            "499.90, 49.99, ON_TRACK",
            "500.00, 50.00, CAUTION",
            "749.90, 74.99, CAUTION",
            "750.00, 75.00, WARNING",
            "999.90, 99.99, WARNING",
            "1000.00, 100.00, OVER_BUDGET"
    })
    void shouldDetermineCorrectBudgetStatusAtThresholdBoundaries(
            String amountSpentValue,
            String expectedPercentageValue,
            BudgetStatus expectedStatus
    ) {
        // Arrange
        User user =
                TestDataFactory.createUser();

        Category category =
                TestDataFactory.createCategory(
                        user,
                        "Groceries"
                );

        Budget budget =
                TestDataFactory.createBudget(
                        user,
                        category,
                        new BigDecimal("1000.00"),
                        9,
                        2026
                );

        BigDecimal amountSpent =
                new BigDecimal(amountSpentValue);

        LocalDate startDate =
                LocalDate.of(2026, 9, 1);

        LocalDate endDate =
                LocalDate.of(2026, 9, 30);

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
                new BigDecimal(expectedPercentageValue),
                response.percentageUsed()
        );

        assertEquals(
                expectedStatus,
                response.status()
        );
    }

        // ---------------------------------------------------------
    // GET ALL BUDGETS
    // ---------------------------------------------------------

    @Test
    void shouldReturnAllBudgetsForAuthenticatedUser() {
        // Arrange
        User user =
                TestDataFactory.createUser();

        Category groceries =
                TestDataFactory.createCategory(
                        user,
                        "Groceries"
                );

        Category dining =
                TestDataFactory.createCategory(
                        user,
                        "Dining"
                );

        Budget firstBudget =
                TestDataFactory.createBudget(
                        user,
                        groceries,
                        new BigDecimal("700.00"),
                        9,
                        2026
                );

        Budget secondBudget =
                TestDataFactory.createBudget(
                        user,
                        dining,
                        new BigDecimal("400.00"),
                        8,
                        2026
                );

        when(userRepository.findByEmail(TEST_EMAIL))
                .thenReturn(Optional.of(user));

        when(
                budgetRepository
                        .findAllByUserIdOrderByYearDescMonthDesc(
                                user.getId()
                        )
        ).thenReturn(
                List.of(
                        firstBudget,
                        secondBudget
                )
        );

        // Act
        List<BudgetResponse> responses =
                budgetService.getAllBudgets(
                        TEST_EMAIL
                );

        // Assert
        assertEquals(
                2,
                responses.size()
        );

        assertEquals(
                "Groceries",
                responses.get(0).categoryName()
        );

        assertEquals(
                new BigDecimal("700.00"),
                responses.get(0).monthlyLimit()
        );

        assertEquals(
                "Dining",
                responses.get(1).categoryName()
        );

        verify(budgetRepository)
                .findAllByUserIdOrderByYearDescMonthDesc(
                        user.getId()
                );
    }

    // ---------------------------------------------------------
    // ADDITIONAL CREATE NEGATIVE TEST
    // ---------------------------------------------------------

    @Test
    void shouldThrowCategoryNotFoundWhenCreatingBudgetWithNonOwnedCategory() {
        // Arrange
        User user =
                TestDataFactory.createUser();

        CreateBudgetRequest request =
                new CreateBudgetRequest(
                        999L,
                        new BigDecimal("700.00"),
                        9,
                        2026
                );

        when(userRepository.findByEmail(TEST_EMAIL))
                .thenReturn(Optional.of(user));

        when(categoryRepository.findByIdAndUserId(
                request.categoryId(),
                user.getId()
        )).thenReturn(Optional.empty());

        // Act + Assert
        CategoryNotFoundException exception =
                assertThrows(
                        CategoryNotFoundException.class,
                        () -> budgetService.createBudget(
                                TEST_EMAIL,
                                request
                        )
                );

        assertEquals(
                "Category not found",
                exception.getMessage()
        );

        verify(
                budgetRepository,
                never()
        ).existsByUserIdAndCategoryIdAndMonthAndYear(
                any(),
                any(),
                any(Integer.class),
                any(Integer.class)
        );

        verify(
                budgetRepository,
                never()
        ).save(any(Budget.class));
    }

    // ---------------------------------------------------------
    // ADDITIONAL UPDATE TESTS
    // ---------------------------------------------------------

    @Test
void shouldUpdateBudgetWhenIdentityIsUnchanged() {
    // Arrange
    User user =
            TestDataFactory.createUser();

    Budget budget =
            mock(Budget.class);

    Category category =
            mock(Category.class);

    UpdateBudgetRequest request =
            new UpdateBudgetRequest(
                    1L,
                    new BigDecimal("850.00"),
                    9,
                    2026
            );

    when(userRepository.findByEmail(TEST_EMAIL))
            .thenReturn(Optional.of(user));

    when(budgetRepository.findByIdAndUserId(
            BUDGET_ID,
            user.getId()
    )).thenReturn(Optional.of(budget));

    when(categoryRepository.findByIdAndUserId(
            request.categoryId(),
            user.getId()
    )).thenReturn(Optional.of(category));

    when(budget.getCategory())
            .thenReturn(category);

    when(category.getId())
            .thenReturn(1L);

    when(budget.getMonth())
            .thenReturn(9);

    when(budget.getYear())
            .thenReturn(2026);

    when(budgetRepository.save(budget))
            .thenReturn(budget);

    when(budget.getMonthlyLimit())
            .thenReturn(new BigDecimal("850.00"));

    when(budget.getMonth())
            .thenReturn(9);

    when(budget.getYear())
            .thenReturn(2026);

    when(category.getName())
            .thenReturn("Groceries");

    // Act
    BudgetResponse response =
            budgetService.updateBudget(
                    TEST_EMAIL,
                    BUDGET_ID,
                    request
            );

    // Assert
    assertEquals(
            new BigDecimal("850.00"),
            response.monthlyLimit()
    );

    assertEquals(
            "Groceries",
            response.categoryName()
    );

    assertEquals(
            9,
            response.month()
    );

    assertEquals(
            2026,
            response.year()
    );

    verify(
            budgetRepository,
            never()
    ).existsByUserIdAndCategoryIdAndMonthAndYear(
            any(),
            any(),
            any(Integer.class),
            any(Integer.class)
    );

    verify(budget)
            .update(
                    category,
                    request.monthlyLimit(),
                    request.month(),
                    request.year()
            );

    verify(budgetRepository)
            .save(budget);
}
    @Test
void shouldUpdateBudgetWhenIdentityChangesAndNoDuplicateExists() {
    // Arrange
    User user =
            TestDataFactory.createUser();

    Budget budget =
            mock(Budget.class);

    Category originalCategory =
            mock(Category.class);

    Category updatedCategory =
            mock(Category.class);

    UpdateBudgetRequest request =
            new UpdateBudgetRequest(
                    2L,
                    new BigDecimal("900.00"),
                    10,
                    2026
            );

    when(userRepository.findByEmail(TEST_EMAIL))
            .thenReturn(Optional.of(user));

    when(budgetRepository.findByIdAndUserId(
            BUDGET_ID,
            user.getId()
    )).thenReturn(Optional.of(budget));

    when(categoryRepository.findByIdAndUserId(
            request.categoryId(),
            user.getId()
    )).thenReturn(Optional.of(updatedCategory));

    when(budget.getCategory())
        .thenReturn(
                originalCategory,
                updatedCategory
        );
        
     when(originalCategory.getId())
        .thenReturn(1L);

     when(updatedCategory.getId())
        .thenReturn(2L);

     when(budgetRepository
        .existsByUserIdAndCategoryIdAndMonthAndYear(
                user.getId(),
                updatedCategory.getId(),
                request.month(),
                request.year()
        ))
        .thenReturn(false);

      when(budgetRepository.save(budget))
        .thenReturn(budget);

      when(budget.getMonthlyLimit())
        .thenReturn(new BigDecimal("900.00"));

       when(budget.getMonth())
        .thenReturn(10);

       when(budget.getYear())
        .thenReturn(2026);

       when(updatedCategory.getName())
        .thenReturn("Dining");

    // Act
    BudgetResponse response =
            budgetService.updateBudget(
                    TEST_EMAIL,
                    BUDGET_ID,
                    request
            );

    // Assert
    assertEquals(
            "Dining",
            response.categoryName()
    );

    assertEquals(
            new BigDecimal("900.00"),
            response.monthlyLimit()
    );

    assertEquals(
            10,
            response.month()
    );

    assertEquals(
            2026,
            response.year()
    );

    verify(budgetRepository)
            .existsByUserIdAndCategoryIdAndMonthAndYear(
                    user.getId(),
                    updatedCategory.getId(),
                    request.month(),
                    request.year()
            );

    verify(budget)
            .update(
                    updatedCategory,
                    request.monthlyLimit(),
                    request.month(),
                    request.year()
            );

    verify(budgetRepository)
            .save(budget);
}
    @Test
    void shouldThrowBudgetNotFoundWhenUpdatingMissingBudget() {
        // Arrange
        User user =
                TestDataFactory.createUser();

        UpdateBudgetRequest request =
                new UpdateBudgetRequest(
                        1L,
                        new BigDecimal("900.00"),
                        9,
                        2026
                );

        when(userRepository.findByEmail(TEST_EMAIL))
                .thenReturn(Optional.of(user));

        when(budgetRepository.findByIdAndUserId(
                BUDGET_ID,
                user.getId()
        )).thenReturn(Optional.empty());

        // Act + Assert
        BudgetNotFoundException exception =
                assertThrows(
                        BudgetNotFoundException.class,
                        () -> budgetService.updateBudget(
                                TEST_EMAIL,
                                BUDGET_ID,
                                request
                        )
                );

        assertEquals(
                "Budget not found",
                exception.getMessage()
        );

        verify(
                categoryRepository,
                never()
        ).findByIdAndUserId(
                request.categoryId(),
                user.getId()
        );

        verify(
                budgetRepository,
                never()
        ).save(any(Budget.class));
    }

    @Test
    void shouldThrowCategoryNotFoundWhenUpdatingWithNonOwnedCategory() {
        // Arrange
        User user =
                TestDataFactory.createUser();

        Category originalCategory =
                TestDataFactory.createCategory(
                        user,
                        "Groceries"
                );

        Budget budget =
                TestDataFactory.createBudget(
                        user,
                        originalCategory,
                        new BigDecimal("700.00"),
                        9,
                        2026
                );

        UpdateBudgetRequest request =
                new UpdateBudgetRequest(
                        999L,
                        new BigDecimal("900.00"),
                        10,
                        2026
                );

        when(userRepository.findByEmail(TEST_EMAIL))
                .thenReturn(Optional.of(user));

        when(budgetRepository.findByIdAndUserId(
                BUDGET_ID,
                user.getId()
        )).thenReturn(Optional.of(budget));

        when(categoryRepository.findByIdAndUserId(
                request.categoryId(),
                user.getId()
        )).thenReturn(Optional.empty());

        // Act + Assert
        CategoryNotFoundException exception =
                assertThrows(
                        CategoryNotFoundException.class,
                        () -> budgetService.updateBudget(
                                TEST_EMAIL,
                                BUDGET_ID,
                                request
                        )
                );

        assertEquals(
                "Category not found",
                exception.getMessage()
        );

        verify(
                budgetRepository,
                never()
        ).save(any(Budget.class));
    }

    // ---------------------------------------------------------
    // ADDITIONAL DELETE NEGATIVE TEST
    // ---------------------------------------------------------

    @Test
    void shouldThrowBudgetNotFoundWhenDeletingMissingBudget() {
        // Arrange
        User user =
                TestDataFactory.createUser();

        when(userRepository.findByEmail(TEST_EMAIL))
                .thenReturn(Optional.of(user));

        when(budgetRepository.findByIdAndUserId(
                BUDGET_ID,
                user.getId()
        )).thenReturn(Optional.empty());

        // Act + Assert
        BudgetNotFoundException exception =
                assertThrows(
                        BudgetNotFoundException.class,
                        () -> budgetService.deleteBudget(
                                TEST_EMAIL,
                                BUDGET_ID
                        )
                );

        assertEquals(
                "Budget not found",
                exception.getMessage()
        );

        verify(
                budgetRepository,
                never()
        ).delete(any(Budget.class));
    }

    // ---------------------------------------------------------
    // ADDITIONAL ANALYTICS NEGATIVE TEST
    // ---------------------------------------------------------

    @Test
    void shouldThrowBudgetNotFoundWhenGettingAnalyticsForMissingBudget() {
        // Arrange
        User user =
                TestDataFactory.createUser();

        when(userRepository.findByEmail(TEST_EMAIL))
                .thenReturn(Optional.of(user));

        when(budgetRepository.findByIdAndUserId(
                BUDGET_ID,
                user.getId()
        )).thenReturn(Optional.empty());

        // Act + Assert
        BudgetNotFoundException exception =
                assertThrows(
                        BudgetNotFoundException.class,
                        () -> budgetService.getBudgetAnalytics(
                                TEST_EMAIL,
                                BUDGET_ID
                        )
                );

        assertEquals(
                "Budget not found",
                exception.getMessage()
        );

        verify(
                transactionRepository,
                never()
        ).sumAmountByUserCategoryTypeAndDateRange(
                any(),
                any(),
                any(),
                any(),
                any()
        );
    }
}