package dev.portfolio.finance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import dev.portfolio.finance.dto.category.CategorySummaryListResponse;
import dev.portfolio.finance.dto.category.CategorySummaryResponse;
import dev.portfolio.finance.entity.BudgetStatus;
import dev.portfolio.finance.entity.BuiltInCategory;
import dev.portfolio.finance.entity.Category;
import dev.portfolio.finance.entity.TransactionType;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.repository.BudgetRepository;
import dev.portfolio.finance.repository.CategoryRepository;
import dev.portfolio.finance.repository.TransactionRepository;
import dev.portfolio.finance.repository.UserRepository;
import dev.portfolio.finance.repository.projection.CategoryBudgetCountProjection;
import dev.portfolio.finance.repository.projection.CategoryTransactionUsageProjection;
import dev.portfolio.finance.repository.projection.CurrentMonthBudgetProjection;

@ExtendWith(MockitoExtension.class)
class CategorySummaryServiceTest {

    private static final String EMAIL = "owner@example.com";
    private static final LocalDate START = LocalDate.of(2026, 10, 1);
    private static final LocalDate END = LocalDate.of(2026, 10, 31);

    @Mock private UserRepository userRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private BudgetRepository budgetRepository;

    private CategorySummaryService service;
    private User user;

    record Usage(Long getCategoryId, Long getTransactionCount, Long getCurrentMonthTransactionCount,
                 LocalDate getLastTransactionDate, BigDecimal getAllTimeSpent, BigDecimal getCurrentMonthSpent)
            implements CategoryTransactionUsageProjection {
    }

    record BudgetCount(Long getCategoryId, Long getBudgetCount) implements CategoryBudgetCountProjection {
    }

    record MonthBudget(Long getBudgetId, Long getCategoryId, BigDecimal getMonthlyLimit)
            implements CurrentMonthBudgetProjection {
    }

    @BeforeEach
    void setUp() {
        Clock october = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC);
        service = new CategorySummaryService(userRepository, categoryRepository, transactionRepository,
                budgetRepository, new ReportingPeriodProvider(october));
        user = new User("Owner", "User", EMAIL, "hash");
        ReflectionTestUtils.setField(user, "id", 1L);
        lenient().when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    }

    private Category category(long id, Category category) {
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }

    private void stub(List<Category> categories, List<CategoryTransactionUsageProjection> usage,
                      List<CategoryBudgetCountProjection> counts, List<CurrentMonthBudgetProjection> budgets) {
        when(categoryRepository.findAllByUserIdOrderByNameAscIdAsc(1L)).thenReturn(categories);
        when(transactionRepository.summarizeUsageByCategory(1L, TransactionType.EXPENSE, START, END)).thenReturn(usage);
        when(budgetRepository.countBudgetsByCategory(1L)).thenReturn(counts);
        when(budgetRepository.findMonthBudgets(1L, 10, 2026)).thenReturn(budgets);
    }

    @Test
    void reportsTheServerMonthAndKeepsTheRepositoryOrder() {
        stub(List.of(category(2, Category.custom(user, "Alpha", true)),
                category(1, Category.custom(user, "Beta", true))), List.of(), List.of(), List.of());

        CategorySummaryListResponse summary = service.getSummary(EMAIL);

        assertThat(summary.month()).isEqualTo(10);
        assertThat(summary.year()).isEqualTo(2026);
        assertThat(summary.categories()).extracting(CategorySummaryResponse::id).containsExactly(2L, 1L);
        verify(transactionRepository).summarizeUsageByCategory(1L, TransactionType.EXPENSE, START, END);
    }

    @Test
    void givesUnusedCategoriesZerosNullsAndTwoDecimalPlaces() {
        stub(List.of(category(5, Category.custom(user, "Unused", false))), List.of(), List.of(), List.of());

        CategorySummaryResponse row = service.getSummary(EMAIL).categories().getFirst();

        assertThat(row.transactionCount()).isZero();
        assertThat(row.currentMonthTransactionCount()).isZero();
        assertThat(row.budgetCount()).isZero();
        assertThat(row.lastTransactionDate()).isNull();
        assertThat(row.currentMonthSpent()).isEqualTo(new BigDecimal("0.00"));
        assertThat(row.allTimeSpent()).isEqualTo(new BigDecimal("0.00"));
        assertThat(row.currentMonthBudget()).isNull();
        assertThat(row.budgetEnabled()).isFalse();
        assertThat(row.canDelete()).isTrue();
    }

    @Test
    void mergesUsageAndBudgetMetricsByCategory() {
        stub(List.of(category(7, Category.custom(user, "Pet Care", true))),
                List.of(new Usage(7L, 4L, 2L, LocalDate.of(2026, 10, 20), new BigDecimal("140"), new BigDecimal("60"))),
                List.of(new BudgetCount(7L, 3L)),
                List.of(new MonthBudget(11L, 7L, new BigDecimal("80.00"))));

        CategorySummaryResponse row = service.getSummary(EMAIL).categories().getFirst();

        assertThat(row.transactionCount()).isEqualTo(4);
        assertThat(row.currentMonthTransactionCount()).isEqualTo(2);
        assertThat(row.budgetCount()).isEqualTo(3);
        assertThat(row.lastTransactionDate()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(row.allTimeSpent()).isEqualTo(new BigDecimal("140.00"));
        assertThat(row.currentMonthSpent()).isEqualTo(new BigDecimal("60.00"));
        assertThat(row.currentMonthBudget().budgetId()).isEqualTo(11L);
        assertThat(row.currentMonthBudget().monthlyLimit()).isEqualTo(new BigDecimal("80.00"));
        assertThat(row.currentMonthBudget().amountSpent()).isEqualTo(new BigDecimal("60.00"));
        assertThat(row.currentMonthBudget().amountRemaining()).isEqualTo(new BigDecimal("20.00"));
        assertThat(row.currentMonthBudget().percentageUsed()).isEqualTo(new BigDecimal("75.00"));
        assertThat(row.currentMonthBudget().status()).isEqualTo(BudgetStatus.WARNING);
        assertThat(row.canDelete()).isFalse();
    }

    @Test
    void allowsDeletingOnlyUnusedCustomCategories() {
        stub(List.of(
                        category(1, Category.builtIn(user, BuiltInCategory.GROCERIES)),
                        category(2, Category.custom(user, "Budget Only", true)),
                        category(3, Category.custom(user, "Income Only", true)),
                        category(4, Category.custom(user, "Free", true))),
                List.of(new Usage(3L, 1L, 0L, LocalDate.of(2026, 1, 5), BigDecimal.ZERO, BigDecimal.ZERO)),
                List.of(new BudgetCount(2L, 1L)),
                List.of());

        List<CategorySummaryResponse> rows = service.getSummary(EMAIL).categories();

        assertThat(rows).extracting(CategorySummaryResponse::canDelete).containsExactly(false, false, false, true);
        assertThat(rows.getFirst().builtIn()).isTrue();
        // Income counts as usage but is never spending.
        assertThat(rows.get(2).transactionCount()).isEqualTo(1);
        assertThat(rows.get(2).allTimeSpent()).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void keepsAHistoricallyUsedCategoryProtectedWhenItHasNoTransactionsThisMonth() {
        // Used last year and budgeted in the past, but quiet this month: still undeletable,
        // because delete eligibility uses the all-time counts.
        stub(List.of(category(6, Category.custom(user, "Holidays", true))),
                List.of(new Usage(6L, 5L, 0L, LocalDate.of(2025, 12, 24), new BigDecimal("300"), BigDecimal.ZERO)),
                List.of(new BudgetCount(6L, 2L)),
                List.of());

        CategorySummaryResponse row = service.getSummary(EMAIL).categories().getFirst();

        assertThat(row.currentMonthTransactionCount()).isZero();
        assertThat(row.transactionCount()).isEqualTo(5);
        assertThat(row.budgetCount()).isEqualTo(2);
        assertThat(row.canDelete()).isFalse();
    }

    @Test
    void mapsAMissingMonthCountToZero() {
        // COUNT never returns null for a group, but the public field must never be null.
        stub(List.of(category(8, Category.custom(user, "Odd", true))),
                List.of(new Usage(8L, 1L, null, LocalDate.of(2026, 10, 2), BigDecimal.ZERO, BigDecimal.ZERO)),
                List.of(), List.of());

        assertThat(service.getSummary(EMAIL).categories().getFirst().currentMonthTransactionCount()).isZero();
    }

    @Test
    void reportsABudgetEvenWhenTheCategoryHasNoSpendingThisMonth() {
        stub(List.of(category(9, Category.custom(user, "Travel", true))), List.of(), List.of(new BudgetCount(9L, 1L)),
                List.of(new MonthBudget(12L, 9L, new BigDecimal("200.00"))));

        CategorySummaryResponse row = service.getSummary(EMAIL).categories().getFirst();

        assertThat(row.currentMonthBudget().amountSpent()).isEqualTo(new BigDecimal("0.00"));
        assertThat(row.currentMonthBudget().percentageUsed()).isEqualByComparingTo("0");
        assertThat(row.currentMonthBudget().status()).isEqualTo(BudgetStatus.ON_TRACK);
    }

    @Test
    void returnsAnEmptyListWhenTheUserHasNoCategories() {
        stub(List.of(), List.of(), List.of(), List.of());

        assertThat(service.getSummary(EMAIL).categories()).isEmpty();
    }

    @Test
    void failsForAnUnknownAuthenticatedUser() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSummary("missing@example.com")).isInstanceOf(NoSuchElementException.class);
    }
}
