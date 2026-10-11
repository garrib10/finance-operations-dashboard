package dev.portfolio.finance.repository;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import dev.portfolio.finance.dto.budget.BudgetMonthAnalyticsResponse;
import dev.portfolio.finance.entity.Category;
import dev.portfolio.finance.entity.TransactionType;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.service.BudgetService;
import dev.portfolio.finance.support.TestDataFactory;
import jakarta.persistence.EntityManager;

/**
 * GET /api/budgets/analytics uses a fixed query plan: exactly three statements (the user,
 * the month's budgets with their categories, and the month's spending grouped by category)
 * for no budgets, 2 budgets, or 15, with or without spending. A per-budget or lazy-loading
 * query would raise the count.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class BudgetMonthAnalyticsQueryCountTest {

    private static final long EXPECTED_STATEMENTS = 3;

    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private BudgetRepository budgetRepository;
    @Autowired private EntityManager entityManager;

    private BudgetService budgetService;

    @BeforeEach
    void createService() {
        // Category selection is only used when saving budgets, never by this read.
        budgetService = new BudgetService(budgetRepository, null, userRepository, transactionRepository);
    }

    @Test
    void usesThreeStatementsForAMonthWithNoBudgets() {
        String email = seed("none@example.com", 0, true);

        assertThat(statementsFor(email, 0)).isEqualTo(EXPECTED_STATEMENTS);
    }

    @Test
    void usesTheSameThreeStatementsForTwoBudgetsAndFifteen() {
        String small = seed("small@example.com", 2, true);
        String large = seed("large@example.com", 15, true);

        long smallCount = statementsFor(small, 2);
        long largeCount = statementsFor(large, 15);

        assertThat(largeCount).isEqualTo(smallCount).isEqualTo(EXPECTED_STATEMENTS);
    }

    @Test
    void usesTheSameThreeStatementsWhetherOrNotAnyBudgetHasSpending() {
        String quiet = seed("quiet@example.com", 8, false);
        String busy = seed("busy@example.com", 8, true);

        assertThat(statementsFor(quiet, 8)).isEqualTo(EXPECTED_STATEMENTS);
        assertThat(statementsFor(busy, 8)).isEqualTo(EXPECTED_STATEMENTS);
    }

    /**
     * March 2024 budgets of 100.00 per category, each with 12.34 spent when requested, plus
     * February budgets and spending that must never be read for March.
     */
    private String seed(String email, int categories, boolean withSpending) {
        User user = userRepository.save(TestDataFactory.createUser("Query", "Count", email));
        for (int i = 0; i < categories; i++) {
            Category category = categoryRepository.save(Category.custom(user, "Category " + i, true));
            budgetRepository.save(TestDataFactory.createBudget(user, category, new BigDecimal("100.00"), 3, 2024));
            budgetRepository.save(TestDataFactory.createBudget(user, category, new BigDecimal("50.00"), 2, 2024));
            if (withSpending) {
                transactionRepository.save(TestDataFactory.createTransaction(user, category, TransactionType.EXPENSE,
                        new BigDecimal("12.34"), "March " + i, LocalDate.of(2024, 3, 10)));
            }
            transactionRepository.save(TestDataFactory.createTransaction(user, category, TransactionType.EXPENSE,
                    new BigDecimal("99.00"), "February " + i, LocalDate.of(2024, 2, 10)));
        }
        return email;
    }

    /** Clears the persistence context and statistics, so only the analytics call is counted. */
    private long statementsFor(String email, int expectedRows) {
        entityManager.flush();
        entityManager.clear();
        Statistics statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        BudgetMonthAnalyticsResponse response = budgetService.getMonthAnalytics(email, "3", "2024");

        // Every figure, including the category name and icon, is read inside the measured window.
        assertThat(response.budgets()).hasSize(expectedRows).allSatisfy(row -> {
            assertThat(row.monthlyLimit()).isEqualByComparingTo("100.00");
            assertThat(row.categoryName()).startsWith("Category ");
            assertThat(row.categoryIconKey()).isNotBlank();
            assertThat(row.month()).isEqualTo(3);
        });
        return statistics.getPrepareStatementCount();
    }
}
