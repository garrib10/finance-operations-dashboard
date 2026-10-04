package dev.portfolio.finance.repository;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import dev.portfolio.finance.entity.Category;
import dev.portfolio.finance.entity.TransactionType;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.repository.projection.CategoryBudgetCountProjection;
import dev.portfolio.finance.repository.projection.CategoryTransactionUsageProjection;
import dev.portfolio.finance.repository.projection.CurrentMonthBudgetProjection;
import dev.portfolio.finance.support.TestDataFactory;
import jakarta.persistence.EntityManager;

/** The category-summary aggregates on the test database: counts, sums, boundaries, ownership. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CategorySummaryQueryTest {

    private static final LocalDate START = LocalDate.of(2026, 10, 1);
    private static final LocalDate END = LocalDate.of(2026, 10, 31);

    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private BudgetRepository budgetRepository;
    @Autowired private EntityManager entityManager;

    private User owner;
    private Category pets;
    private Category salary;
    private Category unused;

    @BeforeEach
    void seed() {
        owner = userRepository.save(TestDataFactory.createUser("Owner", "One", "summary-owner@example.com"));
        pets = categoryRepository.save(Category.custom(owner, "Pet Care", true));
        salary = categoryRepository.save(Category.custom(owner, "Salary", false));
        unused = categoryRepository.save(Category.custom(owner, "Unused", true));

        expense(owner, pets, "10.25", LocalDate.of(2026, 9, 30));   // last day of the previous month
        expense(owner, pets, "20.50", LocalDate.of(2026, 10, 1));   // first day
        expense(owner, pets, "30.00", LocalDate.of(2026, 10, 31));  // last day (future-dated in the month)
        expense(owner, pets, "5.00", LocalDate.of(2026, 11, 1));    // next month
        transactionRepository.save(TestDataFactory.createTransaction(owner, pets, TransactionType.INCOME,
                new BigDecimal("99.00"), "Refund", LocalDate.of(2026, 10, 15)));
        transactionRepository.save(TestDataFactory.createTransaction(owner, salary, TransactionType.INCOME,
                new BigDecimal("3000.00"), "Pay", LocalDate.of(2026, 10, 5)));

        budgetRepository.save(TestDataFactory.createBudget(owner, pets, new BigDecimal("80.00"), 10, 2026));
        budgetRepository.save(TestDataFactory.createBudget(owner, pets, new BigDecimal("70.00"), 9, 2026));
        budgetRepository.save(TestDataFactory.createBudget(owner, pets, new BigDecimal("60.00"), 10, 2025));

        // Another user with a same-named category must never leak into the owner's results.
        User other = userRepository.save(TestDataFactory.createUser("Other", "Two", "summary-other@example.com"));
        Category otherPets = categoryRepository.save(Category.custom(other, "Pet Care", true));
        expense(other, otherPets, "500.00", LocalDate.of(2026, 10, 10));
        budgetRepository.save(TestDataFactory.createBudget(other, otherPets, new BigDecimal("900.00"), 10, 2026));

        entityManager.flush();
        entityManager.clear();
    }

    private void expense(User user, Category category, String amount, LocalDate date) {
        transactionRepository.save(TestDataFactory.createTransaction(user, category, TransactionType.EXPENSE,
                new BigDecimal(amount), "Expense", date));
    }

    private Map<Long, CategoryTransactionUsageProjection> usage() {
        return transactionRepository.summarizeUsageByCategory(owner.getId(), TransactionType.EXPENSE, START, END)
                .stream().collect(Collectors.toMap(CategoryTransactionUsageProjection::getCategoryId, Function.identity()));
    }

    @Test
    void countsAllTransactionsButSumsOnlyExpenses() {
        CategoryTransactionUsageProjection petUsage = usage().get(pets.getId());

        assertThat(petUsage.getTransactionCount()).isEqualTo(5);
        assertThat(petUsage.getAllTimeSpent()).isEqualByComparingTo("65.75");
        assertThat(petUsage.getLastTransactionDate()).isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @Test
    void countsTheReportingMonthsTransactionsOfBothTypesInclusiveOfBothEnds() {
        // 1 Oct expense, 15 Oct income refund, 31 Oct (future-dated) expense; 30 Sep and
        // 1 Nov are outside the month. Income counts as activity even though it is not spending.
        CategoryTransactionUsageProjection petUsage = usage().get(pets.getId());

        assertThat(petUsage.getCurrentMonthTransactionCount()).isEqualTo(3);
        assertThat(petUsage.getTransactionCount()).isEqualTo(5); // All time stays separate.
        assertThat(usage().get(salary.getId()).getCurrentMonthTransactionCount()).isEqualTo(1);
    }

    @Test
    void sumsTheReportingMonthInclusiveOfBothEnds() {
        // 20.50 (1 Oct) + 30.00 (31 Oct); 30 Sep, 1 Nov, and the income refund are excluded.
        assertThat(usage().get(pets.getId()).getCurrentMonthSpent()).isEqualByComparingTo("50.50");
    }

    @Test
    void reportsIncomeOnlyCategoriesWithZeroSpending() {
        CategoryTransactionUsageProjection salaryUsage = usage().get(salary.getId());

        assertThat(salaryUsage.getTransactionCount()).isEqualTo(1);
        assertThat(salaryUsage.getAllTimeSpent()).isEqualByComparingTo("0");
        assertThat(salaryUsage.getCurrentMonthSpent()).isEqualByComparingTo("0");
    }

    @Test
    void omitsUnusedCategoriesAndOtherUsersData() {
        assertThat(usage()).containsOnlyKeys(pets.getId(), salary.getId());
        assertThat(usage()).doesNotContainKey(unused.getId());
    }

    @Test
    void countsBudgetsInEveryMonthPerCategory() {
        Map<Long, Long> counts = budgetRepository.countBudgetsByCategory(owner.getId()).stream()
                .collect(Collectors.toMap(CategoryBudgetCountProjection::getCategoryId,
                        CategoryBudgetCountProjection::getBudgetCount));

        assertThat(counts).containsExactly(Map.entry(pets.getId(), 3L));
    }

    @Test
    void findsOnlyTheReportingMonthsBudgetsForTheUser() {
        assertThat(budgetRepository.findMonthBudgets(owner.getId(), 10, 2026))
                .singleElement()
                .satisfies(budget -> {
                    assertThat(budget.getCategoryId()).isEqualTo(pets.getId());
                    assertThat(budget.getMonthlyLimit()).isEqualByComparingTo("80.00");
                    assertThat(budget.getBudgetId()).isNotNull();
                });
        assertThat(budgetRepository.findMonthBudgets(owner.getId(), 12, 2026)).isEmpty();
    }
}
