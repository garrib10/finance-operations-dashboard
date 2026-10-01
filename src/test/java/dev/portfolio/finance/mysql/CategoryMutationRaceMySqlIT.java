package dev.portfolio.finance.mysql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import dev.portfolio.finance.dto.category.CreateCategoryRequest;
import dev.portfolio.finance.dto.category.UpdateCategoryRequest;
import dev.portfolio.finance.exception.category.CategoryInUseException;
import dev.portfolio.finance.exception.category.DuplicateCategoryException;
import dev.portfolio.finance.service.CategoryService;

/**
 * Category mutation races on real InnoDB locks. A competing write is left uncommitted on a
 * separate connection, the service call runs until InnoDB reports it in LOCK WAIT behind
 * that write, and only then does the competitor commit or roll back. The application's
 * existence checks cannot see the uncommitted row, so these prove the database safeguards
 * and their translation, not the friendly prechecks.
 */
class CategoryMutationRaceMySqlIT extends MySqlIntegrationTestBase {

    @Autowired private CategoryService categoryService;

    @Test
    void renameLosingToAConcurrentCreateReturnsDuplicateAndChangesNothing() throws Exception {
        String email = insertUser();
        long userId = userId(email);
        long dining = categoryService.createCategory(email, new CreateCategoryRequest("Dining Out", true)).id();

        try (Connection competitor = rootConnection("fintrack")) {
            competitor.setAutoCommit(false);
            insertCategory(competitor, userId, "Takeout", "takeout");

            Future<?> rename = runAsync(() -> categoryService.updateCategory(email, dining,
                    new UpdateCategoryRequest("TAKEOUT", true)));
            new LockGate().awaitCompetitorBlocked();
            competitor.commit();

            assertThat(failureOf(rename)).isInstanceOf(DuplicateCategoryException.class);
        }
        assertThat(jdbc.queryForObject("SELECT CONCAT(name, '|', normalized_name) FROM categories WHERE id = ?",
                String.class, dining)).isEqualTo("Dining Out|dining out");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM categories WHERE user_id = ? AND normalized_name = "
                + "'takeout'", Long.class, userId)).isEqualTo(1);
    }

    @Test
    void renameSucceedsWhenTheConcurrentCreateRollsBack() throws Exception {
        String email = insertUser();
        long userId = userId(email);
        long dining = categoryService.createCategory(email, new CreateCategoryRequest("Dining Out", true)).id();

        try (Connection competitor = rootConnection("fintrack")) {
            competitor.setAutoCommit(false);
            insertCategory(competitor, userId, "Takeout", "takeout");

            Future<?> rename = runAsync(() -> categoryService.updateCategory(email, dining,
                    new UpdateCategoryRequest("Takeout", true)));
            new LockGate().awaitCompetitorBlocked();
            competitor.rollback();

            rename.get(60, TimeUnit.SECONDS);
        }
        assertThat(jdbc.queryForObject("SELECT normalized_name FROM categories WHERE id = ?", String.class, dining))
                .isEqualTo("takeout");
    }

    @Test
    void deleteLosingToAConcurrentReferenceReturnsInUseAndLeavesBothRows() throws Exception {
        String email = insertUser();
        long userId = userId(email);
        long pets = categoryService.createCategory(email, new CreateCategoryRequest("Pets", true)).id();

        long transactionId;
        try (Connection competitor = rootConnection("fintrack")) {
            competitor.setAutoCommit(false);
            transactionId = insertTransaction(competitor, userId, pets);

            Future<?> delete = runAsync(() -> categoryService.deleteCategory(email, pets));
            new LockGate().awaitCompetitorBlocked();
            competitor.commit();

            assertThat(failureOf(delete)).isInstanceOf(CategoryInUseException.class);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM categories WHERE id = ?", Long.class, pets)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT category_id FROM transactions WHERE id = ?", Long.class, transactionId))
                .isEqualTo(pets);
    }

    @Test
    void referenceLosingToACommittedDeleteFailsWithoutADanglingRow() throws Exception {
        String email = insertUser();
        long userId = userId(email);
        long pets = categoryService.createCategory(email, new CreateCategoryRequest("Pets", true)).id();

        categoryService.deleteCategory(email, pets);

        assertThatThrownBy(() -> jdbc.update("INSERT INTO transactions (created_at,updated_at,amount,description,"
                + "transaction_date,type,user_id,category_id) VALUES (NOW(6),NOW(6),1.00,'x',CURDATE(),'EXPENSE',?,?)",
                userId, pets)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO budgets (created_at,updated_at,month,monthly_limit,year,"
                + "category_id,user_id) VALUES (NOW(6),NOW(6),1,10.00,2026,?,?)", pets, userId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions WHERE category_id = ?", Long.class, pets))
                .isZero();
    }

    // ------------------------------------------------------------------ helpers

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
        } catch (java.util.concurrent.ExecutionException failed) {
            return failed.getCause();
        }
    }

    private String insertUser() {
        String email = "race-" + UUID.randomUUID() + "@example.com";
        jdbc.update("INSERT INTO users (created_at,updated_at,first_name,last_name,display_name,email,password_hash) "
                + "VALUES (NOW(6),NOW(6),'F','L','F L',?,'hash')", email);
        return email;
    }

    private long userId(String email) {
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
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

    private static long insertTransaction(Connection connection, long userId, long categoryId) throws Exception {
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO transactions (created_at,updated_at,"
                + "amount,description,transaction_date,type,user_id,category_id) VALUES (NOW(6),NOW(6),4.50,'Food',"
                + "CURDATE(),'EXPENSE',?,?)", java.sql.Statement.RETURN_GENERATED_KEYS)) {
            insert.setLong(1, userId);
            insert.setLong(2, categoryId);
            insert.executeUpdate();
            try (var keys = insert.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }
}
