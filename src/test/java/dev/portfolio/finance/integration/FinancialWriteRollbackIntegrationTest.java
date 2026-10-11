package dev.portfolio.finance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import dev.portfolio.finance.dto.budget.CreateBudgetRequest;
import dev.portfolio.finance.dto.category.NewCategoryRequest;
import dev.portfolio.finance.dto.transaction.CreateTransactionRequest;
import dev.portfolio.finance.entity.Budget;
import dev.portfolio.finance.entity.Transaction;
import dev.portfolio.finance.entity.TransactionType;
import dev.portfolio.finance.repository.BudgetRepository;
import dev.portfolio.finance.repository.TransactionRepository;
import dev.portfolio.finance.service.BudgetService;
import dev.portfolio.finance.service.TransactionService;

/**
 * A financial write that fails at the database after its new category was inserted rolls
 * the category back too. The repository save is made to fail inside the real transactional
 * service; the assertions read the committed database state.
 */
@SpringBootTest
@ActiveProfiles("test")
class FinancialWriteRollbackIntegrationTest {

    @Autowired private TransactionService transactionService;
    @Autowired private BudgetService budgetService;
    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private TransactionRepository transactionRepository;
    @MockitoSpyBean private BudgetRepository budgetRepository;

    private String email;
    private long userId;

    @BeforeEach
    void user() {
        email = "rollback-" + UUID.randomUUID() + "@example.com";
        jdbc.update("INSERT INTO users (created_at, updated_at, first_name, last_name, display_name, email, "
                + "password_hash, date_format, transaction_page_size) VALUES (CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, "
                + "'R', 'B', 'R B', ?, 'hash', 'MEDIUM', 10)", email);
        userId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    @Test
    void failedTransactionInsertRollsBackItsNewCategory() {
        doThrow(new DataIntegrityViolationException("simulated database failure"))
                .when(transactionRepository).saveAndFlush(any(Transaction.class));

        assertThatThrownBy(() -> transactionService.createTransaction(email, new CreateTransactionRequest(null,
                TransactionType.EXPENSE, new BigDecimal("4.50"), "Coffee", LocalDate.of(2026, 9, 30),
                new NewCategoryRequest("Coffee Shops", "coffee"))))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(rows("categories")).isZero();
        assertThat(rows("transactions")).isZero();
    }

    @Test
    void failedBudgetInsertRollsBackItsNewCategory() {
        doThrow(new DataIntegrityViolationException("simulated database failure"))
                .when(budgetRepository).saveAndFlush(any(Budget.class));

        assertThatThrownBy(() -> budgetService.createBudget(email, new CreateBudgetRequest(null,
                new BigDecimal("40.00"), 9, 2026, new NewCategoryRequest("Gym", "dumbbell"))))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(rows("categories")).isZero();
        assertThat(rows("budgets")).isZero();
    }

    private long rows(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE user_id = ?", Long.class, userId);
    }
}
