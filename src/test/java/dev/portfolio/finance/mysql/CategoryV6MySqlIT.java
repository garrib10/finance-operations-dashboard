package dev.portfolio.finance.mysql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.assertj.core.util.Throwables;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import dev.portfolio.finance.dto.auth.RegisterRequest;
import dev.portfolio.finance.dto.category.CreateCategoryRequest;
import dev.portfolio.finance.exception.category.DuplicateCategoryException;
import dev.portfolio.finance.service.CategoryService;
import dev.portfolio.finance.service.UserService;

/**
 * V6 on the pinned MySQL server: a production-shaped upgrade (Hibernate-generated foreign-key
 * names, utf8mb4_0900_ai_ci tables, Flyway baseline at V1), binary uniqueness, composite
 * ownership keys, the icon CHECK, preflight failure, and concurrent equivalent creates.
 */
class CategoryV6MySqlIT extends MySqlIntegrationTestBase {

    @Autowired private CategoryService categoryService;
    @Autowired private UserService userService;

    // ------------------------------------------------------------------ upgrade

    @Test
    void productionShapedV5DatabaseUpgradesToV6PreservingData() throws Exception {
        String schema = productionShapedV5Schema();
        try (Connection connection = rootConnection(schema); Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO users (id,created_at,updated_at,first_name,last_name,display_name,"
                    + "email,password_hash) VALUES (1,'2026-01-01 00:00:00.000001','2026-01-01 00:00:00.000002',"
                    + "'A','B','A B','one@example.com','hash-one'),(2,NOW(6),NOW(6),'C','D','C D','two@example.com',"
                    + "'hash-two')");
            statement.executeUpdate("INSERT INTO categories (id,created_at,updated_at,budget_enabled,name,user_id) VALUES "
                    + "(10,'2026-01-02 00:00:00.000001','2026-01-03 00:00:00.000002',b'1','Housing',1),"
                    + "(11,NOW(6),NOW(6),b'1',' groceries ',1),"
                    + "(12,NOW(6),NOW(6),b'0','Income',1),"
                    + "(13,NOW(6),NOW(6),b'1','Pet  Supplies',1),"
                    + "(14,NOW(6),NOW(6),b'1','Other',1),"
                    + "(20,NOW(6),NOW(6),b'1','Café',2),"
                    + "(21,NOW(6),NOW(6),b'0','SAVINGS',2)");
            statement.executeUpdate("INSERT INTO transactions (id,created_at,updated_at,amount,description,"
                    + "transaction_date,type,user_id,category_id) VALUES "
                    + "(100,'2026-03-01 00:00:00.5','2026-03-01 00:00:00.5',12.50,'Lunch','2026-03-01','EXPENSE',1,11),"
                    + "(101,NOW(6),NOW(6),900.00,'Pay','2026-03-02','INCOME',1,12),"
                    + "(102,NOW(6),NOW(6),3.75,'Coffee','2026-03-03','EXPENSE',2,20)");
            statement.executeUpdate("INSERT INTO budgets (id,created_at,updated_at,month,monthly_limit,year,"
                    + "category_id,user_id) VALUES (200,NOW(6),NOW(6),3,400.00,2026,11,1),"
                    + "(201,NOW(6),NOW(6),3,25.00,2026,20,2)");
        }
        Map<String, List<String>> before = snapshot(schema);

        Flyway upgrade = flyway(schema, "latest");
        assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);

        MigrationInfo v6 = upgrade.info().current();
        assertThat(v6.getVersion().getVersion()).isEqualTo("6");
        assertThat(v6.getType().name()).isEqualTo("JDBC");
        assertThat(v6.getChecksum()).isEqualTo(1_060_001);
        assertThat(snapshot(schema)).isEqualTo(before);
        assertThat(rows(schema, "SELECT CONCAT(id, ' ', normalized_name, ' ', built_in + 0, ' ', icon_key) "
                + "FROM categories ORDER BY id")).containsExactly(
                "10 housing 1 house",
                "11 groceries 1 shopping-cart",
                "12 income 1 circle-dollar-sign",
                "13 pet supplies 0 tag",
                "14 other 1 tag",
                "20 café 0 tag",
                "21 savings 1 piggy-bank");

        assertThat(rows(schema, "SELECT CONCAT(COLUMN_NAME, ' ', COLUMN_TYPE, ' ', IS_NULLABLE, ' ', "
                + "COALESCE(COLLATION_NAME, '-')) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                + "AND TABLE_NAME = 'categories' AND COLUMN_NAME IN ('normalized_name','built_in','icon_key') "
                + "ORDER BY COLUMN_NAME")).containsExactly(
                "built_in bit(1) NO -",
                "icon_key varchar(64) NO utf8mb4_0900_bin",
                "normalized_name varchar(300) NO utf8mb4_0900_bin");
        assertThat(rows(schema, "SELECT CONCAT(TABLE_NAME, '.', CONSTRAINT_NAME, ' ', CONSTRAINT_TYPE) "
                + "FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = DATABASE() "
                + "AND TABLE_NAME IN ('categories','transactions','budgets') AND CONSTRAINT_NAME NOT LIKE 'FKlegacy%' "
                + "AND CONSTRAINT_TYPE <> 'PRIMARY KEY' ORDER BY 1")).containsExactly(
                "budgets.fk_budgets_category_owner FOREIGN KEY",
                "budgets.uk_budget_user_category_month_year UNIQUE",
                "categories.ck_categories_icon_key_format CHECK",
                "categories.uk_categories_id_user UNIQUE",
                "categories.uk_categories_user_normalized_name UNIQUE",
                "transactions.fk_transactions_category_owner FOREIGN KEY");
        // Hibernate-named legacy foreign keys are untouched.
        assertThat(rows(schema, "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = "
                + "DATABASE() AND CONSTRAINT_NAME LIKE 'FKlegacy%'")).containsExactly("5");

        assertThat(upgrade.migrate().migrationsExecuted).isZero();
        upgrade.validate();
        assertThat(snapshot(schema)).isEqualTo(before);
    }

    @Test
    void collisionFailsPreflightOnMysqlBeforeAnyDdl() throws Exception {
        String schema = productionShapedV5Schema();
        try (Connection connection = rootConnection(schema); Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO users (id,created_at,updated_at,first_name,last_name,display_name,"
                    + "email,password_hash) VALUES (1,NOW(6),NOW(6),'A','B','A B','one@example.com','hash')");
            // Distinct under the legacy NO PAD accent-insensitive key, equal after normalization.
            statement.executeUpdate("INSERT INTO categories (id,created_at,updated_at,budget_enabled,name,user_id) "
                    + "VALUES (1,NOW(6),NOW(6),b'1','Eating Out',1),(2,NOW(6),NOW(6),b'1','Eating  Out',1)");
        }
        Map<String, List<String>> before = snapshot(schema);
        List<String> columnsBefore = rows(schema, "SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE "
                + "TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'categories' ORDER BY ORDINAL_POSITION");

        Throwable failure = org.assertj.core.api.Assertions.catchThrowable(() -> flyway(schema, "latest").migrate());

        assertThat(Throwables.getRootCause(failure).getMessage()).startsWith(
                "V6 preflight failed; no schema or data changes were made").contains(
                "user 1 has categories whose names normalize to the same value: ids [1, 2]")
                .doesNotContain("Eating");
        assertThat(snapshot(schema)).isEqualTo(before);
        assertThat(rows(schema, "SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                + "AND TABLE_NAME = 'categories' ORDER BY ORDINAL_POSITION")).isEqualTo(columnsBefore);
        assertThat(rows(schema, "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() "
                + "AND INDEX_NAME = 'uk_category_user_name'")).containsExactly("2");
        assertThat(rows(schema, "SELECT CONCAT(COUNT(*), ' rows, ', COALESCE(SUM(success), 0), ' successful') "
                + "FROM flyway_schema_history WHERE version = '6'")).containsExactly("1 rows, 0 successful");

        // Recovery: resolve the reported rows by hand, repair the failed history row, rerun.
        try (Connection connection = rootConnection(schema); Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE categories SET name = 'Dining Out' WHERE id = 2");
        }
        Flyway retry = flyway(schema, "latest");
        retry.repair();
        assertThat(retry.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(rows(schema, "SELECT CONCAT(id, ' ', normalized_name) FROM categories ORDER BY id"))
                .containsExactly("1 eating out", "2 dining out");
    }

    // ------------------------------------------------------------------ constraints on the live schema

    @Test
    void mysqlEnforcesExactUniquenessOwnershipRestrictionAndIconFormat() {
        long owner = insertUser("owner");
        long other = insertUser("other");
        long food = insertCategory(owner, "Food", "food", "tag");
        insertCategory(other, "food", "food", "tag");
        // utf8mb4_0900_bin: accents, case, and trailing spaces are all significant.
        insertCategory(owner, "Café", "café", "tag");
        insertCategory(owner, "Cafe", "cafe", "tag");
        insertCategory(owner, "Upper", "FOOD", "tag");
        insertCategory(owner, "Padded", "food ", "tag");
        insertCategory(owner, "Straße", "straße", "tag");
        insertCategory(owner, "STRASSE", "strasse", "heart-pulse");

        assertThatThrownBy(() -> insertCategory(owner, "FOOD", "food", "tag"))
                .isInstanceOf(DuplicateKeyException.class);
        for (String icon : List.of("<svg/>", "https://example.com/i.png", "../tag", "House", "fa fa-home", "")) {
            // MySQL error 3819; Spring does not categorize CHECK violations.
            assertThatThrownBy(() -> insertCategory(owner, "Icon " + icon.length(), "icon " + icon.length(), icon))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class)
                    .hasMessageContaining("ck_categories_icon_key_format");
        }

        assertThatThrownBy(() -> jdbc.update("INSERT INTO transactions (created_at,updated_at,amount,description,"
                + "transaction_date,type,user_id,category_id) VALUES (NOW(6),NOW(6),1.00,'x',CURDATE(),'EXPENSE',?,?)",
                other, food)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO budgets (created_at,updated_at,month,monthly_limit,year,"
                + "category_id,user_id) VALUES (NOW(6),NOW(6),1,10.00,2026,?,?)", food, other))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbc.update("INSERT INTO transactions (created_at,updated_at,amount,description,transaction_date,type,"
                + "user_id,category_id) VALUES (NOW(6),NOW(6),1.00,'x',CURDATE(),'EXPENSE',?,?)", owner, food);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM categories WHERE id = ?", food))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE transactions SET user_id = ? WHERE category_id = ?", other, food))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void registrationSeedsTheBuiltInCatalogOnMysql() {
        String email = "seed-" + UUID.randomUUID() + "@example.com";
        userService.register(new RegisterRequest("Seed", "User", email, "Str0ng!Password"));

        assertThat(jdbc.queryForList("SELECT CONCAT(c.name, ' ', c.normalized_name, ' ', c.built_in + 0, ' ', "
                + "c.icon_key, ' ', c.budget_enabled + 0) FROM categories c JOIN users u ON u.id = c.user_id "
                + "WHERE u.email = ? ORDER BY c.id", String.class, email)).containsExactly(
                "Housing housing 1 house 1",
                "Groceries groceries 1 shopping-cart 1",
                "Dining dining 1 utensils 1",
                "Transportation transportation 1 car 1",
                "Utilities utilities 1 lightbulb 1",
                "Insurance insurance 1 shield 1",
                "Healthcare healthcare 1 heart-pulse 1",
                "Entertainment entertainment 1 clapperboard 1",
                "Shopping shopping 1 shopping-bag 1",
                "Travel travel 1 plane 1",
                "Income income 1 circle-dollar-sign 0",
                "Savings savings 1 piggy-bank 0",
                "Other other 1 tag 1");
    }

    @Test
    void concurrentEquivalentCreatesLeaveExactlyOneCategory() throws Exception {
        long userId = insertUser("race");
        String email = jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, userId);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (String name : List.of("Eating Out", "  EATING   out ")) {
                Callable<String> create = () -> {
                    start.await();
                    try {
                        categoryService.createCategory(email, new CreateCategoryRequest(name, true));
                        return "created";
                    } catch (DuplicateCategoryException duplicate) {
                        return "duplicate";
                    }
                };
                results.add(pool.submit(create));
            }
            start.countDown();
            List<String> outcomes = new ArrayList<>();
            for (Future<String> result : results) {
                outcomes.add(result.get(30, TimeUnit.SECONDS));
            }
            assertThat(outcomes).containsExactlyInAnyOrder("created", "duplicate");
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM categories WHERE user_id = ? AND normalized_name = "
                + "'eating out'", Long.class, userId)).isEqualTo(1);
    }

    // ------------------------------------------------------------------ helpers

    private long insertUser(String prefix) {
        String email = prefix + "-" + UUID.randomUUID() + "@example.com";
        jdbc.update("INSERT INTO users (created_at,updated_at,first_name,last_name,display_name,email,password_hash) "
                + "VALUES (NOW(6),NOW(6),'F','L','F L',?,'hash')", email);
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private long insertCategory(long userId, String name, String normalized, String icon) {
        jdbc.update("INSERT INTO categories (created_at,updated_at,budget_enabled,name,user_id,normalized_name,"
                + "built_in,icon_key) VALUES (NOW(6),NOW(6),b'1',?,?,?,b'0',?)", name, userId, normalized, icon);
        return jdbc.queryForObject("SELECT id FROM categories WHERE user_id = ? AND normalized_name = ? COLLATE "
                + "utf8mb4_0900_bin", Long.class, userId, normalized);
    }

    /**
     * Mirrors production: tables created by Hibernate (generated FK and index names, default
     * utf8mb4_0900_ai_ci collation, explicit uk_category_user_name), adopted by a Flyway V1
     * baseline, then upgraded to V5.
     */
    private static String productionShapedV5Schema() throws SQLException {
        String schema = "v6_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        try (Connection root = rootConnection("fintrack"); Statement statement = root.createStatement()) {
            statement.executeUpdate("CREATE DATABASE " + schema + " DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }
        try (Connection connection = rootConnection(schema); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE users (id BIGINT NOT NULL AUTO_INCREMENT, created_at DATETIME(6) NOT NULL, "
                    + "updated_at DATETIME(6) NOT NULL, email VARCHAR(255) NOT NULL, first_name VARCHAR(100) NOT NULL, "
                    + "last_name VARCHAR(100) NOT NULL, password_hash VARCHAR(255) NOT NULL, PRIMARY KEY (id), "
                    + "CONSTRAINT uk_users_email UNIQUE (email))");
            statement.execute("CREATE TABLE categories (id BIGINT NOT NULL AUTO_INCREMENT, created_at DATETIME(6) NOT NULL, "
                    + "updated_at DATETIME(6) NOT NULL, budget_enabled BIT(1) NOT NULL, name VARCHAR(100) NOT NULL, "
                    + "user_id BIGINT NOT NULL, PRIMARY KEY (id), UNIQUE KEY uk_category_user_name (user_id, name), "
                    + "CONSTRAINT FKlegacyCategoryUser FOREIGN KEY (user_id) REFERENCES users (id))");
            statement.execute("CREATE TABLE transactions (id BIGINT NOT NULL AUTO_INCREMENT, created_at DATETIME(6) NOT NULL, "
                    + "updated_at DATETIME(6) NOT NULL, amount DECIMAL(12,2) NOT NULL, description VARCHAR(255) NOT NULL, "
                    + "transaction_date DATE NOT NULL, type ENUM('EXPENSE','INCOME') NOT NULL, user_id BIGINT NOT NULL, "
                    + "category_id BIGINT NOT NULL, PRIMARY KEY (id), KEY FKlegacyTxCategory (category_id), "
                    + "CONSTRAINT FKlegacyTxUser FOREIGN KEY (user_id) REFERENCES users (id), "
                    + "CONSTRAINT FKlegacyTxCategory FOREIGN KEY (category_id) REFERENCES categories (id))");
            statement.execute("CREATE TABLE budgets (id BIGINT NOT NULL AUTO_INCREMENT, created_at DATETIME(6) NOT NULL, "
                    + "updated_at DATETIME(6) NOT NULL, month INT NOT NULL, monthly_limit DECIMAL(12,2) NOT NULL, "
                    + "year INT NOT NULL, category_id BIGINT NOT NULL, user_id BIGINT NOT NULL, PRIMARY KEY (id), "
                    + "UNIQUE KEY uk_budget_user_category_month_year (user_id, category_id, month, year), "
                    + "KEY FKlegacyBudgetCategory (category_id), "
                    + "CONSTRAINT FKlegacyBudgetUser FOREIGN KEY (user_id) REFERENCES users (id), "
                    + "CONSTRAINT FKlegacyBudgetCategory FOREIGN KEY (category_id) REFERENCES categories (id))");
        }
        Flyway.configure().dataSource(MYSQL.getJdbcUrl().replace("/fintrack", "/" + schema), "root", MYSQL.getPassword())
                .locations("classpath:db/migration").baselineOnMigrate(true).baselineVersion("1").target("5")
                .load().migrate();
        return schema;
    }

    private static Flyway flyway(String schema, String target) {
        return Flyway.configure().dataSource(MYSQL.getJdbcUrl().replace("/fintrack", "/" + schema), "root",
                MYSQL.getPassword()).locations("classpath:db/migration").target(target).load();
    }

    private static List<String> rows(String schema, String sql) throws SQLException {
        List<String> result = new ArrayList<>();
        try (Connection connection = rootConnection(schema); Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                result.add(rows.getString(1));
            }
        }
        return result;
    }

    /** Every pre-V6 column of the tables V6 touches or references. */
    private static Map<String, List<String>> snapshot(String schema) throws SQLException {
        Map<String, String> tables = new LinkedHashMap<>();
        tables.put("users", "*");
        tables.put("categories", "id, created_at, updated_at, budget_enabled + 0 AS budget_enabled, name, user_id");
        tables.put("transactions", "*");
        tables.put("budgets", "*");
        Map<String, List<String>> result = new LinkedHashMap<>();
        try (Connection connection = rootConnection(schema); Statement statement = connection.createStatement()) {
            for (Map.Entry<String, String> table : tables.entrySet()) {
                List<String> values = new ArrayList<>();
                try (ResultSet rows = statement.executeQuery(
                        "SELECT " + table.getValue() + " FROM " + table.getKey() + " ORDER BY id")) {
                    while (rows.next()) {
                        for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) {
                            values.add(rows.getMetaData().getColumnLabel(i).toLowerCase() + "=" + rows.getString(i));
                        }
                    }
                }
                result.put(table.getKey(), values);
            }
        }
        return result;
    }
}
