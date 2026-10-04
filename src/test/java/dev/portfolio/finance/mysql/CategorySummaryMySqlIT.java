package dev.portfolio.finance.mysql;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import dev.portfolio.finance.dto.category.CategorySummaryResponse;
import dev.portfolio.finance.entity.BudgetStatus;
import dev.portfolio.finance.entity.Category;
import dev.portfolio.finance.entity.TransactionType;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.repository.BudgetRepository;
import dev.portfolio.finance.repository.CategoryRepository;
import dev.portfolio.finance.repository.TransactionRepository;
import dev.portfolio.finance.repository.UserRepository;
import dev.portfolio.finance.service.CategorySummaryService;
import dev.portfolio.finance.support.TestDataFactory;

/** The summary's conditional sums, MAX date, counts, and decimal scale on real MySQL. */
class CategorySummaryMySqlIT extends MySqlIntegrationTestBase {

    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private BudgetRepository budgetRepository;
    @Autowired private CategorySummaryService summaryService;

    @Test
    void aggregatesUsageWithExactDecimalsOnMySql() {
        String email = "summary-" + UUID.randomUUID() + "@example.com";
        User user = userRepository.save(TestDataFactory.createUser("Summary", "MySQL", email));
        Category pets = categoryRepository.save(Category.custom(user, "Pet Care", true));
        Category unused = categoryRepository.save(Category.custom(user, "Unused", true));
        LocalDate today = LocalDate.now();
        LocalDate lastMonth = today.minusMonths(1);

        save(user, pets, TransactionType.EXPENSE, "0.10", today);
        save(user, pets, TransactionType.EXPENSE, "0.20", today);
        save(user, pets, TransactionType.EXPENSE, "9999999999.99", lastMonth);
        save(user, pets, TransactionType.INCOME, "50.00", today);
        budgetRepository.save(TestDataFactory.createBudget(user, pets, new BigDecimal("0.40"),
                today.getMonthValue(), today.getYear()));
        budgetRepository.save(TestDataFactory.createBudget(user, pets, new BigDecimal("1.00"),
                lastMonth.getMonthValue(), lastMonth.getYear()));

        CategorySummaryResponse petRow = row(email, pets.getId());
        assertThat(petRow.transactionCount()).isEqualTo(4);
        // COUNT(CASE …) on MySQL: today's two expenses and the income, not last month's expense.
        assertThat(petRow.currentMonthTransactionCount()).isEqualTo(3);
        assertThat(petRow.budgetCount()).isEqualTo(2);
        assertThat(petRow.lastTransactionDate()).isEqualTo(today);
        // Exact decimal arithmetic: 0.10 + 0.20 is 0.30, never a binary floating-point approximation.
        assertThat(petRow.currentMonthSpent()).isEqualTo(new BigDecimal("0.30"));
        assertThat(petRow.allTimeSpent()).isEqualTo(new BigDecimal("10000000000.29"));
        assertThat(petRow.currentMonthBudget().percentageUsed()).isEqualTo(new BigDecimal("75.00"));
        assertThat(petRow.currentMonthBudget().status()).isEqualTo(BudgetStatus.WARNING);

        CategorySummaryResponse unusedRow = row(email, unused.getId());
        assertThat(unusedRow.currentMonthTransactionCount()).isZero();
        assertThat(unusedRow.currentMonthSpent()).isEqualTo(new BigDecimal("0.00"));
        assertThat(unusedRow.allTimeSpent()).isEqualTo(new BigDecimal("0.00"));
        assertThat(unusedRow.canDelete()).isTrue();
    }

    private void save(User user, Category category, TransactionType type, String amount, LocalDate date) {
        transactionRepository.save(TestDataFactory.createTransaction(user, category, type, new BigDecimal(amount),
                "MySQL summary", date));
    }

    private CategorySummaryResponse row(String email, Long categoryId) {
        return summaryService.getSummary(email).categories().stream()
                .filter(row -> row.id().equals(categoryId))
                .findFirst()
                .orElseThrow();
    }
}
