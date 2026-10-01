package dev.portfolio.finance.mysql;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import dev.portfolio.finance.dto.budget.CreateBudgetRequest;
import dev.portfolio.finance.dto.category.NewCategoryRequest;
import dev.portfolio.finance.dto.transaction.CreateTransactionRequest;
import dev.portfolio.finance.entity.TransactionType;
import dev.portfolio.finance.exception.budget.DuplicateBudgetException;
import dev.portfolio.finance.exception.category.DuplicateCategoryException;
import dev.portfolio.finance.service.BudgetService;
import dev.portfolio.finance.service.TransactionService;

/**
 * Financial writes that create or use a category, racing on real InnoDB locks. A competing
 * write stays uncommitted until the service call is confirmed blocked behind it
 * ({@link LockGate}), then commits. The losing request must leave no category, transaction,
 * or budget behind, and is never retried or redirected to the winning category.
 */
class FinancialCategoryRaceMySqlIT extends MySqlIntegrationTestBase {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 15);

    @Autowired private TransactionService transactionService;
    @Autowired private BudgetService budgetService;

    @Test
    void transactionCreatingACategoryLosesToAConcurrentEquivalentCategory() throws Exception {
        String email = insertUser();
        long userId = userId(email);

        try (Connection competitor = rootConnection("fintrack")) {
            competitor.setAutoCommit(false);
            insertCategory(competitor, userId, "Pet Care", "pet care");

            Future<?> write = runAsync(() -> transactionService.createTransaction(email, newCategoryTransaction(
                    "PET  care")));
            new LockGate().awaitCompetitorBlocked();
            competitor.commit();

            assertThat(failureOf(write)).isInstanceOf(DuplicateCategoryException.class);
        }
        assertThat(count("SELECT COUNT(*) FROM categories WHERE user_id = ? AND normalized_name = 'pet care'", userId))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM transactions WHERE user_id = ?", userId)).isZero();
    }

    @Test
    void budgetCreatingACategoryLosesToAConcurrentEquivalentCategory() throws Exception {
        String email = insertUser();
        long userId = userId(email);

        try (Connection competitor = rootConnection("fintrack")) {
            competitor.setAutoCommit(false);
            insertCategory(competitor, userId, "Gym", "gym");

            Future<?> write = runAsync(() -> budgetService.createBudget(email, new CreateBudgetRequest(
                    null, new BigDecimal("40.00"), 9, 2026, new NewCategoryRequest("GYM", "dumbbell"))));
            new LockGate().awaitCompetitorBlocked();
            competitor.commit();

            assertThat(failureOf(write)).isInstanceOf(DuplicateCategoryException.class);
        }
        assertThat(count("SELECT COUNT(*) FROM categories WHERE user_id = ? AND normalized_name = 'gym'", userId))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM budgets WHERE user_id = ?", userId)).isZero();
    }

    @Test
    void newCategoryLosesToAConcurrentRenameOfAnotherCategory() throws Exception {
        String email = insertUser();
        long userId = userId(email);
        long dining = insertCommittedCategory(userId, "Dining Out", "dining out");

        try (Connection competitor = rootConnection("fintrack")) {
            competitor.setAutoCommit(false);
            try (PreparedStatement rename = competitor.prepareStatement(
                    "UPDATE categories SET name = 'Takeout', normalized_name = 'takeout' WHERE id = ?")) {
                rename.setLong(1, dining);
                rename.executeUpdate();
            }

            Future<?> write = runAsync(() -> transactionService.createTransaction(email,
                    newCategoryTransaction("Takeout")));
            new LockGate().awaitCompetitorBlocked();
            competitor.commit();

            assertThat(failureOf(write)).isInstanceOf(DuplicateCategoryException.class);
        }
        assertThat(count("SELECT COUNT(*) FROM categories WHERE user_id = ?", userId)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM transactions WHERE user_id = ?", userId)).isZero();
    }

    @Test
    void transactionLosingToAConcurrentCategoryDeleteLeavesNoDanglingRow() throws Exception {
        String email = insertUser();
        long userId = userId(email);
        long pets = insertCommittedCategory(userId, "Pets", "pets");

        try (Connection competitor = rootConnection("fintrack")) {
            competitor.setAutoCommit(false);
            try (PreparedStatement delete = competitor.prepareStatement("DELETE FROM categories WHERE id = ?")) {
                delete.setLong(1, pets);
                delete.executeUpdate();
            }

            Future<?> write = runAsync(() -> transactionService.createTransaction(email, new CreateTransactionRequest(
                    pets, TransactionType.EXPENSE, new BigDecimal("9.00"), "Food", DATE)));
            new LockGate().awaitCompetitorBlocked();
            competitor.commit();

            // The restrictive foreign key rejects the reference; nothing is created.
            assertThat(failureOf(write)).isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThat(count("SELECT COUNT(*) FROM categories WHERE id = ?", pets)).isZero();
        assertThat(count("SELECT COUNT(*) FROM transactions WHERE user_id = ?", userId)).isZero();
    }

    @Test
    void concurrentBudgetForTheSameCategoryAndMonthReturnsDuplicateBudget() throws Exception {
        String email = insertUser();
        long userId = userId(email);
        long groceries = insertCommittedCategory(userId, "Groceries", "groceries");

        try (Connection competitor = rootConnection("fintrack")) {
            competitor.setAutoCommit(false);
            try (PreparedStatement insert = competitor.prepareStatement("INSERT INTO budgets (created_at,updated_at,"
                    + "month,monthly_limit,year,category_id,user_id) VALUES (NOW(6),NOW(6),9,100.00,2026,?,?)")) {
                insert.setLong(1, groceries);
                insert.setLong(2, userId);
                insert.executeUpdate();
            }

            Future<?> write = runAsync(() -> budgetService.createBudget(email,
                    new CreateBudgetRequest(groceries, new BigDecimal("50.00"), 9, 2026)));
            new LockGate().awaitCompetitorBlocked();
            competitor.commit();

            assertThat(failureOf(write)).isInstanceOf(DuplicateBudgetException.class);
        }
        assertThat(count("SELECT COUNT(*) FROM budgets WHERE user_id = ?", userId)).isEqualTo(1);
    }

    @Test
    void unsynchronizedEquivalentTransactionsCreateExactlyOneCategoryAndOneTransaction() throws Exception {
        String email = insertUser();
        long userId = userId(email);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<String> outcomes = new ArrayList<>();
        try {
            List<Future<String>> results = new ArrayList<>();
            for (String name : List.of("Coffee", "  COFFEE ")) {
                Callable<String> write = () -> {
                    start.await();
                    try {
                        transactionService.createTransaction(email, newCategoryTransaction(name));
                        return "created";
                    } catch (DuplicateCategoryException duplicate) {
                        return "duplicate";
                    }
                };
                results.add(pool.submit(write));
            }
            start.countDown();
            for (Future<String> result : results) {
                outcomes.add(result.get(60, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(outcomes).containsExactlyInAnyOrder("created", "duplicate");
        assertThat(count("SELECT COUNT(*) FROM categories WHERE user_id = ? AND normalized_name = 'coffee'", userId))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM transactions WHERE user_id = ?", userId)).isEqualTo(1);
    }

    // ------------------------------------------------------------------ helpers

    private static CreateTransactionRequest newCategoryTransaction(String name) {
        return new CreateTransactionRequest(null, TransactionType.EXPENSE, new BigDecimal("4.50"), "Purchase", DATE,
                new NewCategoryRequest(name, null));
    }

    private static Future<?> runAsync(Runnable call) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            return executor.submit(call);
        } finally {
            executor.shutdown();
        }
    }

    private static Throwable failureOf(Future<?> call) throws Exception {
        try {
            call.get(60, TimeUnit.SECONDS);
            throw new AssertionError("Expected the operation to fail");
        } catch (ExecutionException failed) {
            return failed.getCause();
        }
    }

    private long count(String sql, long id) {
        return jdbc.queryForObject(sql, Long.class, id);
    }

    private String insertUser() {
        String email = "fin-race-" + UUID.randomUUID() + "@example.com";
        jdbc.update("INSERT INTO users (created_at,updated_at,first_name,last_name,display_name,email,password_hash) "
                + "VALUES (NOW(6),NOW(6),'F','L','F L',?,'hash')", email);
        return email;
    }

    private long userId(String email) {
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private long insertCommittedCategory(long userId, String name, String normalized) {
        jdbc.update("INSERT INTO categories (created_at,updated_at,budget_enabled,name,user_id,normalized_name,"
                + "built_in,icon_key) VALUES (NOW(6),NOW(6),b'1',?,?,?,b'0','tag')", name, userId, normalized);
        return jdbc.queryForObject("SELECT id FROM categories WHERE user_id = ? AND normalized_name = ?",
                Long.class, userId, normalized);
    }

    private static void insertCategory(Connection connection, long userId, String name, String normalized)
            throws Exception {
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO categories (created_at,updated_at,"
                + "budget_enabled,name,user_id,normalized_name,built_in,icon_key) VALUES (NOW(6),NOW(6),b'1',?,?,?,"
                + "b'0','tag')")) {
            insert.setString(1, name);
            insert.setLong(2, userId);
            insert.setString(3, normalized);
            insert.executeUpdate();
        }
    }
}
