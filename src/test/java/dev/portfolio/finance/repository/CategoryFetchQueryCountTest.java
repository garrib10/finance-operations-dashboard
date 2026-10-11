package dev.portfolio.finance.repository;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import dev.portfolio.finance.entity.Category;
import dev.portfolio.finance.entity.Transaction;
import dev.portfolio.finance.entity.TransactionType;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.specification.TransactionSpecification;
import dev.portfolio.finance.support.TestDataFactory;
import jakarta.persistence.EntityManager;

/** Listing transactions or budgets reads category name and icon without a query per row. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class CategoryFetchQueryCountTest {

    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private BudgetRepository budgetRepository;
    @Autowired private EntityManager entityManager;

    private User user;
    private Statistics statistics;

    @BeforeEach
    void fiveTransactionsAndBudgetsInFiveCategories() {
        user = userRepository.save(new User("Query", "Count", "query-count@example.com", "hash"));
        for (int i = 0; i < 5; i++) {
            Category category = categoryRepository.save(Category.custom(user, "Category " + i, true));
            transactionRepository.save(TestDataFactory.createTransaction(user, category, TransactionType.EXPENSE,
                    new BigDecimal("1.00"), "Row " + i, LocalDate.of(2026, 9, i + 1)));
            budgetRepository.save(TestDataFactory.createBudget(user, category, new BigDecimal("10.00"), 9, 2026));
        }
        entityManager.flush();
        entityManager.clear();
        statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
    }

    @Test
    void pagedTransactionSearchLoadsCategoriesWithThePage() {
        List<Transaction> page = transactionRepository.findAll(
                TransactionSpecification.belongsToUser(user.getId()),
                PageRequest.of(0, 10, Sort.by("transactionDate"))).getContent();
        page.forEach(transaction -> transaction.getCategory().getIcon().key().length());

        assertThat(page).hasSize(5);
        // One select for the page; no count query is needed for a final partial page.
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void listQueriesUsedByTheDashboardAndBudgetsLoadCategoriesInOneSelect() {
        transactionRepository.findTop5ByUserIdOrderByTransactionDateDescCreatedAtDesc(user.getId())
                .forEach(transaction -> transaction.getCategory().getName().length());
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);

        statistics.clear();
        entityManager.clear();
        budgetRepository.findAllByUserIdOrderByYearDescMonthDesc(user.getId())
                .forEach(budget -> budget.getCategory().getIcon().key().length());
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);

        statistics.clear();
        entityManager.clear();
        transactionRepository.findAllByUserIdOrderByTransactionDateDesc(user.getId())
                .forEach(transaction -> transaction.getCategory().getName().length());
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }
}
