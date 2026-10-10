package dev.portfolio.finance.mysql;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import dev.portfolio.finance.dto.budget.BudgetAnalyticsResponse;
import dev.portfolio.finance.dto.budget.BudgetMonthAnalyticsResponse;
import dev.portfolio.finance.entity.BudgetStatus;
import dev.portfolio.finance.entity.Category;
import dev.portfolio.finance.entity.TransactionType;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.repository.BudgetRepository;
import dev.portfolio.finance.repository.CategoryRepository;
import dev.portfolio.finance.repository.TransactionRepository;
import dev.portfolio.finance.repository.UserRepository;
import dev.portfolio.finance.service.BudgetService;
import dev.portfolio.finance.support.TestDataFactory;

/** One month's budgets with analytics on real MySQL: grouped sums, boundaries, scale, ownership, order. */
class BudgetMonthAnalyticsMySqlIT extends MySqlIntegrationTestBase {

    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private BudgetRepository budgetRepository;
    @Autowired private BudgetService budgetService;

    @Test
    void aggregatesTheMonthsExpensesPerCategoryWithExactDecimalsOnMySql() {
        String email = "month-" + UUID.randomUUID() + "@example.com";
        User user = userRepository.save(TestDataFactory.createUser("Month", "MySQL", email));
        Category pets = categoryRepository.save(Category.custom(user, "Pet Care", true));
        Category rent = categoryRepository.save(Category.custom(user, "Apartment Rent", true));
        Category travel = categoryRepository.save(Category.custom(user, "Weekend Trips", true));
        LocalDate start = LocalDate.of(2028, 2, 1);
        LocalDate end = LocalDate.of(2028, 2, 29);

        save(user, pets, TransactionType.EXPENSE, "0.10", start);              // first day: in
        save(user, pets, TransactionType.EXPENSE, "0.20", end);                // last day (leap day): in
        save(user, pets, TransactionType.INCOME, "50.00", end);                // income: never spending
        save(user, pets, TransactionType.EXPENSE, "3.00", start.minusDays(1)); // the month before: out
        save(user, pets, TransactionType.EXPENSE, "4.00", end.plusDays(1));    // the month after: out
        save(user, travel, TransactionType.EXPENSE, "9.99", start);            // spending without a budget
        budget(user, pets, "0.40", 2, 2028);
        budget(user, rent, "1200.00", 2, 2028);                                // no spending at all
        budget(user, pets, "1.00", 1, 2028);                                   // another month

        // Another user's same-named category, budget, and spending never mix in.
        User other = userRepository.save(TestDataFactory.createUser("Other", "MySQL",
                "month-other-" + UUID.randomUUID() + "@example.com"));
        Category otherPets = categoryRepository.save(Category.custom(other, "Pet Care", true));
        save(other, otherPets, TransactionType.EXPENSE, "500.00", start);
        budget(other, otherPets, "900.00", 2, 2028);

        BudgetMonthAnalyticsResponse month = budgetService.getMonthAnalytics(email, "2", "2028");

        assertThat(month.budgets()).extracting(BudgetAnalyticsResponse::categoryName)
                .containsExactly("Apartment Rent", "Pet Care");
        BudgetAnalyticsResponse petRow = month.budgets().get(1);
        assertThat(petRow.categoryId()).isEqualTo(pets.getId());
        // Exact decimal arithmetic: 0.10 + 0.20 is 0.30, never a floating-point approximation.
        assertThat(petRow.amountSpent()).isEqualTo(new BigDecimal("0.30"));
        assertThat(petRow.amountRemaining()).isEqualTo(new BigDecimal("0.10"));
        assertThat(petRow.percentageUsed()).isEqualTo(new BigDecimal("75.00"));
        assertThat(petRow.status()).isEqualTo(BudgetStatus.WARNING);
        BudgetAnalyticsResponse rentRow = month.budgets().get(0);
        assertThat(rentRow.amountSpent()).isEqualTo(new BigDecimal("0.00"));
        assertThat(rentRow.amountRemaining()).isEqualTo(new BigDecimal("1200.00"));
        assertThat(rentRow.percentageUsed()).isEqualTo(new BigDecimal("0.00"));

        // The same figures as the single-budget analytics on MySQL.
        BudgetAnalyticsResponse single = budgetService.getBudgetAnalytics(email, petRow.budgetId());
        assertThat(single.amountSpent()).isEqualByComparingTo(petRow.amountSpent());
        assertThat(single.percentageUsed()).isEqualTo(petRow.percentageUsed());
        assertThat(single.status()).isEqualTo(petRow.status());
    }

    @Test
    void returnsAnEmptyMonthOnMySql() {
        String email = "month-empty-" + UUID.randomUUID() + "@example.com";
        User user = userRepository.save(TestDataFactory.createUser("Month", "Empty", email));
        Category pets = categoryRepository.save(Category.custom(user, "Pet Care", true));
        budget(user, pets, "10.00", 3, 2024);
        save(user, pets, TransactionType.EXPENSE, "5.00", LocalDate.of(2024, 4, 2));

        BudgetMonthAnalyticsResponse month = budgetService.getMonthAnalytics(email, "4", "2024");

        assertThat(month.month()).isEqualTo(4);
        assertThat(month.year()).isEqualTo(2024);
        assertThat(month.budgets()).isEmpty();
    }

    private void save(User user, Category category, TransactionType type, String amount, LocalDate date) {
        transactionRepository.save(TestDataFactory.createTransaction(user, category, type, new BigDecimal(amount),
                "MySQL month", date));
    }

    private void budget(User user, Category category, String limit, int month, int year) {
        budgetRepository.save(TestDataFactory.createBudget(user, category, new BigDecimal(limit), month, year));
    }
}
