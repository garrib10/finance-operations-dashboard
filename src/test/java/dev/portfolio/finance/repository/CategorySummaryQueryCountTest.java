package dev.portfolio.finance.repository;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import dev.portfolio.finance.entity.Category;
import dev.portfolio.finance.entity.TransactionType;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.service.CategorySummaryService;
import dev.portfolio.finance.service.ReportingPeriodProvider;
import dev.portfolio.finance.support.TestDataFactory;
import jakarta.persistence.EntityManager;

/**
 * The summary uses a fixed query plan: the same number of statements for 2 categories as
 * for 12, so adding categories can never add per-category queries.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Import({CategorySummaryService.class, ReportingPeriodProvider.class})
class CategorySummaryQueryCountTest {

    /** User, categories, transaction aggregate, budget counts, and the month's budgets. */
    private static final long PLANNED_STATEMENTS = 5;

    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private BudgetRepository budgetRepository;
    @Autowired private CategorySummaryService summaryService;
    @Autowired private EntityManager entityManager;

    @Test
    void usesTheSameStatementCountForTwoAndTwelveCategories() {
        String small = seed("small@example.com", 2);
        String large = seed("large@example.com", 12);

        long smallCount = statementsFor(small, 2);
        long largeCount = statementsFor(large, 12);

        assertThat(largeCount).isEqualTo(smallCount);
        assertThat(largeCount).isLessThanOrEqualTo(PLANNED_STATEMENTS);
    }

    /** Seeds categories that each have transactions and current and past budgets. */
    private String seed(String email, int categories) {
        User user = userRepository.save(TestDataFactory.createUser("Query", "Count", email));
        LocalDate today = LocalDate.now();
        for (int i = 0; i < categories; i++) {
            Category category = categoryRepository.save(Category.custom(user, "Category " + i, true));
            transactionRepository.save(TestDataFactory.createTransaction(user, category, TransactionType.EXPENSE,
                    new BigDecimal("12.34"), "Row " + i, today));
            transactionRepository.save(TestDataFactory.createTransaction(user, category, TransactionType.INCOME,
                    new BigDecimal("5.00"), "Income " + i, today.minusMonths(2)));
            budgetRepository.save(TestDataFactory.createBudget(user, category, new BigDecimal("50.00"),
                    today.getMonthValue(), today.getYear()));
            LocalDate earlier = today.minusMonths(1);
            budgetRepository.save(TestDataFactory.createBudget(user, category, new BigDecimal("40.00"),
                    earlier.getMonthValue(), earlier.getYear()));
        }
        return email;
    }

    /** Clears the persistence context and statistics so only the summary call is measured. */
    private long statementsFor(String email, int expectedRows) {
        entityManager.flush();
        entityManager.clear();
        Statistics statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        // Every row's figures, including this month's count, are read inside the measured
        // window, so a lazy load for any of them would raise the statement count.
        assertThat(summaryService.getSummary(email).categories()).hasSize(expectedRows)
                .allSatisfy(row -> {
                    assertThat(row.currentMonthBudget()).isNotNull();
                    assertThat(row.currentMonthTransactionCount()).isEqualTo(1); // Today's expense only.
                    assertThat(row.transactionCount()).isEqualTo(2);
                });

        return statistics.getPrepareStatementCount();
    }
}
