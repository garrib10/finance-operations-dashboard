package dev.portfolio.finance.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import dev.portfolio.finance.validation.CategoryNameNormalizer;

/** V6 on H2: clean install, populated V5 upgrade, preflight failures, and constraints. */
class V6CategoryMigrationTest {

    /** Checksums of the released V1–V5 scripts; a change means an applied migration was edited. */
    private static final Map<String, Integer> RELEASED_CHECKSUMS = Map.of(
            "1", -2034163446,
            "2", -531054137,
            "3", -4809275,
            "4", -336996864,
            "5", -609379505);

    // ------------------------------------------------------------------ clean install

    @Test
    void cleanInstallAppliesV6AsJavaMigrationWithStableChecksum() throws SQLException {
        String url = databaseUrl("clean");
        Flyway flyway = flyway(url, "latest");

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(6);

        MigrationInfo v6 = flyway.info().current();
        assertThat(v6.getVersion().getVersion()).isEqualTo("6");
        assertThat(v6.getType().name()).isEqualTo("JDBC");
        assertThat(v6.getChecksum()).isEqualTo(1_060_001);
        assertThat(v6.getScript()).isEqualTo("db.migration.V6__add_category_normalization_builtin_and_icons");
        assertThat(flyway.info().pending()).isEmpty();
        flyway.validate();
        assertThat(flyway.migrate().migrationsExecuted).isZero();

        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            Map<String, String> columns = columns(connection.getMetaData(), "categories");
            assertThat(columns).containsEntry("normalized_name", "VARCHAR(300) NOT NULL")
                    .containsEntry("icon_key", "VARCHAR(64) NOT NULL")
                    .containsEntry("name", "VARCHAR(100) NOT NULL");
            assertThat(columns.get("built_in")).endsWith("NOT NULL");
        }
    }

    @Test
    void releasedV1ToV5ScriptsAreUnchanged() {
        Flyway flyway = flyway(databaseUrl("checksums"), "latest");
        flyway.migrate();

        Map<String, Integer> checksums = new TreeMap<>();
        for (MigrationInfo info : flyway.info().applied()) {
            if (!"6".equals(info.getVersion().getVersion())) {
                assertThat(info.getType().name()).isEqualTo("SQL");
                checksums.put(info.getVersion().getVersion(), info.getChecksum());
            }
        }
        assertThat(checksums).isEqualTo(new TreeMap<>(RELEASED_CHECKSUMS));
    }

    @Test
    void v6ReplacesNameUniquenessAndAddsOwnershipStructures() throws SQLException {
        String url = databaseUrl("structure");
        flyway(url, "latest").migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            DatabaseMetaData metadata = connection.getMetaData();
            assertThat(uniqueIndexColumns(metadata, "categories")).contains(
                    List.of("user_id", "normalized_name"), List.of("id", "user_id"))
                    .doesNotContain(List.of("user_id", "name"));
            assertThat(indexColumns(metadata, "transactions")).containsEntry(
                    "idx_transactions_category_user", List.of("category_id", "user_id"));
            assertThat(indexColumns(metadata, "budgets")).containsEntry(
                    "idx_budgets_category_user", List.of("category_id", "user_id"));
            assertThat(foreignKeys(metadata, "transactions")).contains(
                    "fk_transactions_category_owner (category_id,user_id) -> categories (id,user_id)");
            assertThat(foreignKeys(metadata, "budgets")).contains(
                    "fk_budgets_category_owner (category_id,user_id) -> categories (id,user_id)");
        }
    }

    // ------------------------------------------------------------------ populated upgrade

    @Test
    void populatedV5UpgradePreservesDataAndBackfillsMetadata() throws SQLException {
        String url = databaseUrl("upgrade");
        flyway(url, "5").migrate();
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            insertUser(statement, 1, "one@example.com");
            insertUser(statement, 2, "two@example.com");
            insertCategory(statement, 10, 1, "Housing", true, "2026-01-01 10:00:00.000001", "2026-01-02 10:00:00.000002");
            insertCategory(statement, 11, 1, "  groceries ", true, "2026-01-01 11:00:00", "2026-01-01 11:00:01");
            insertCategory(statement, 12, 1, "Food", false, "2026-01-01 12:00:00", "2026-01-01 12:00:00");
            insertCategory(statement, 13, 1, "Other", true, "2026-01-01 13:00:00", "2026-01-01 13:00:00");
            insertCategory(statement, 14, 1, "Income", false, "2026-01-01 14:00:00", "2026-01-01 14:00:00");
            insertCategory(statement, 15, 1, "Home  Repairs", true, "2026-01-01 15:00:00", "2026-01-01 15:00:00");
            insertCategory(statement, 20, 2, "HOUSING", false, "2026-02-01 10:00:00", "2026-02-01 10:00:00");
            insertCategory(statement, 21, 2, "Café", true, "2026-02-01 11:00:00", "2026-02-01 11:00:00");
            insertCategory(statement, 22, 2, "Cafe", true, "2026-02-01 12:00:00", "2026-02-01 12:00:00");
            statement.executeUpdate("INSERT INTO transactions VALUES (100, '2026-03-01 00:00:00.5', "
                    + "'2026-03-01 00:00:00.5', 12.50, 'Lunch', '2026-03-01', 'EXPENSE', 1, 12)");
            statement.executeUpdate("INSERT INTO transactions VALUES (101, '2026-03-02 00:00:00', "
                    + "'2026-03-02 00:00:00', 900.00, 'Pay', '2026-03-02', 'INCOME', 1, 14)");
            statement.executeUpdate("INSERT INTO transactions VALUES (102, '2026-03-03 00:00:00', "
                    + "'2026-03-03 00:00:00', 4.25, 'Coffee', '2026-03-03', 'EXPENSE', 2, 21)");
            statement.executeUpdate("INSERT INTO budgets VALUES (200, '2026-03-04 00:00:00', "
                    + "'2026-03-04 00:00:00', 3, 400.00, 2026, 11, 1)");
            statement.executeUpdate("INSERT INTO budgets VALUES (201, '2026-03-05 00:00:00', "
                    + "'2026-03-05 00:00:00', 3, 50.00, 2026, 22, 2)");
        }
        Map<String, List<String>> before = snapshot(url);

        Flyway flyway = flyway(url, "latest");
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("6");
        assertThat(snapshot(url)).isEqualTo(before);

        assertThat(metadata(url)).containsExactly(
                "10 housing built_in=true house",
                "11 groceries built_in=true shopping-cart",
                "12 food built_in=false tag",
                "13 other built_in=true tag",
                "14 income built_in=true circle-dollar-sign",
                "15 home repairs built_in=false tag",
                "20 housing built_in=true house",
                "21 café built_in=false tag",
                "22 cafe built_in=false tag");

        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(snapshot(url)).isEqualTo(before);
    }

    @Test
    void frozenMigrationNormalizationMatchesApplicationNormalizer() throws SQLException {
        List<String> samples = List.of("Groceries", "  Eating \t Out  ", "Café", "STRAßE", "STRASSE",
                "Ünïcödé  Name", "İstanbul", "Food 🍕", "ΣΊΣΥΦΟΣ");
        String url = databaseUrl("parity");
        flyway(url, "5").migrate();
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement();
             PreparedStatement insert = connection.prepareStatement("INSERT INTO categories "
                     + "(id, created_at, updated_at, budget_enabled, name, user_id) VALUES (?, CURRENT_TIMESTAMP, "
                     + "CURRENT_TIMESTAMP, TRUE, ?, ?)")) {
            for (int i = 0; i < samples.size(); i++) {
                insertUser(statement, i + 1, "user" + i + "@example.com");
                insert.setInt(1, i + 1);
                insert.setString(2, samples.get(i));
                insert.setInt(3, i + 1);
                insert.executeUpdate();
            }
        }
        flyway(url, "latest").migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT name, normalized_name FROM categories ORDER BY id")) {
            while (rows.next()) {
                assertThat(rows.getString(2))
                        .isEqualTo(CategoryNameNormalizer.normalize(rows.getString(1)).comparisonName());
            }
        }
    }

    // ------------------------------------------------------------------ preflight failures

    @Test
    void sameUserNormalizationCollisionFailsBeforeAnyChange() throws SQLException {
        String url = databaseUrl("collision");
        flyway(url, "5").migrate();
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            insertUser(statement, 1, "one@example.com");
            insertUser(statement, 2, "two@example.com");
            insertCategory(statement, 1, 1, "Eating Out", true, "2026-01-01 00:00:00", "2026-01-01 00:00:00");
            insertCategory(statement, 2, 1, "eating  OUT", true, "2026-01-01 00:00:00", "2026-01-01 00:00:00");
            insertCategory(statement, 3, 1, "Café", true, "2026-01-01 00:00:00", "2026-01-01 00:00:00");
            insertCategory(statement, 4, 1, "Café", true, "2026-01-01 00:00:00", "2026-01-01 00:00:00");
            // The same name for a different user is not a collision.
            insertCategory(statement, 5, 2, "Eating Out", true, "2026-01-01 00:00:00", "2026-01-01 00:00:00");
        }

        assertPreflightFailsWithoutChanges(url,
                "user 1 has categories whose names normalize to the same value: ids [1, 2]",
                "user 1 has categories whose names normalize to the same value: ids [3, 4]");
    }

    @Test
    void invalidLegacyNamesFailBeforeAnyChange() throws SQLException {
        String url = databaseUrl("invalid");
        flyway(url, "5").migrate();
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement();
             PreparedStatement insert = connection.prepareStatement("INSERT INTO categories "
                     + "(id, created_at, updated_at, budget_enabled, name, user_id) VALUES (?, CURRENT_TIMESTAMP, "
                     + "CURRENT_TIMESTAMP, TRUE, ?, 1)")) {
            insertUser(statement, 1, "one@example.com");
            String[] names = {"   ", "\t ", "Bad\u0001Name", "Broken\uD800", "Valid"};
            for (int i = 0; i < names.length; i++) {
                insert.setInt(1, i + 1);
                insert.setString(2, names[i]);
                insert.executeUpdate();
            }
        }

        String message = assertPreflightFailsWithoutChanges(url,
                "2 categories have an invalid name (blank): ids [1, 2]",
                "1 category has an invalid name (control character): ids [3]",
                "1 category has an invalid name (malformed): ids [4]");
        assertThat(message).doesNotContain("Bad", "Broken", "Valid");
    }

    @Test
    void crossOwnerAndMissingFinancialReferencesFailBeforeAnyChange() throws SQLException {
        String url = databaseUrl("ownership");
        flyway(url, "5").migrate();
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            insertUser(statement, 1, "one@example.com");
            insertUser(statement, 2, "two@example.com");
            insertCategory(statement, 1, 1, "Food", true, "2026-01-01 00:00:00", "2026-01-01 00:00:00");
            statement.executeUpdate("INSERT INTO transactions VALUES (7, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, "
                    + "9.99, 'Secret description', CURRENT_DATE, 'EXPENSE', 2, 1)");
            statement.executeUpdate("INSERT INTO budgets VALUES (8, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, "
                    + "1, 123.45, 2026, 1, 2)");
            statement.execute("SET REFERENTIAL_INTEGRITY FALSE");
            statement.executeUpdate("INSERT INTO transactions VALUES (9, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, "
                    + "1.00, 'Orphan', CURRENT_DATE, 'EXPENSE', 1, 999)");
            statement.executeUpdate("INSERT INTO budgets VALUES (10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, "
                    + "2, 5.00, 2026, 999, 1)");
            statement.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }

        String message = assertPreflightFailsWithoutChanges(url,
                "1 transactions reference a category owned by another user: ids [7]",
                "1 transactions reference a missing category: ids [9]",
                "1 budgets reference a category owned by another user: ids [8]",
                "1 budgets reference a missing category: ids [10]");
        assertThat(message).doesNotContain("Secret", "9.99", "123.45", "@example.com");
    }

    @Test
    void unexpectedSchemaStateFailsBeforeAnyChange() throws SQLException {
        String partial = databaseUrl("partial");
        flyway(partial, "5").migrate();
        execute(partial, "ALTER TABLE categories ADD COLUMN icon_key VARCHAR(64) NULL");
        execute(partial, "CREATE INDEX idx_budgets_category_user ON budgets (category_id, user_id)");
        assertThatThrownBy(() -> flyway(partial, "latest").migrate())
                .isInstanceOf(FlywayException.class)
                .hasRootCauseMessage("V6 preflight failed; no schema or data changes were made. Resolve these rows "
                        + "manually (V6 never merges, renames, or reassigns data), then rerun: "
                        + "categories.icon_key already exists (partially applied V6?); "
                        + "index idx_budgets_category_user already exists");

        String missingLegacy = databaseUrl("missing_legacy");
        flyway(missingLegacy, "5").migrate();
        execute(missingLegacy, "ALTER TABLE categories DROP CONSTRAINT uk_category_user_name");
        execute(missingLegacy, "ALTER TABLE categories ADD CONSTRAINT uk_categories_id_user UNIQUE (id, user_id)");
        assertThatThrownBy(() -> flyway(missingLegacy, "latest").migrate())
                .isInstanceOf(FlywayException.class)
                .hasStackTraceContaining("expected unique constraint uk_category_user_name on categories "
                        + "(user_id, name) was not found")
                .hasStackTraceContaining("constraint categories.uk_categories_id_user already exists");
    }

    // ------------------------------------------------------------------ constraints after V6

    @Test
    void v6EnforcesNormalizedUniquenessOwnershipRestrictionAndIconFormat() throws SQLException {
        String url = databaseUrl("constraints");
        flyway(url, "latest").migrate();
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            insertUser(statement, 1, "one@example.com");
            insertUser(statement, 2, "two@example.com");
            statement.executeUpdate(category(1, 1, "Food", "food", "tag"));
            statement.executeUpdate(category(2, 2, "food", "food", "tag"));
            statement.executeUpdate(category(3, 1, "Café", "café", "tag"));
            statement.executeUpdate(category(4, 1, "Cafe", "cafe", "tag"));
            statement.executeUpdate(category(5, 1, "Straße", "straße", "tag"));
            statement.executeUpdate(category(6, 1, "STRASSE", "strasse", "heart-pulse"));

            for (String invalid : List.of(
                    category(7, 1, "FOOD", "food", "tag"),
                    category(7, 1, "Pic", "pic", "<svg onload=x>"),
                    category(7, 1, "Pic", "pic", "https://example.com/icon.png"),
                    category(7, 1, "Pic", "pic", "../icons/tag"),
                    category(7, 1, "Pic", "pic", "House"),
                    category(7, 1, "Pic", "pic", "fa fa-home"),
                    category(7, 1, "Pic", "pic", ""),
                    "INSERT INTO categories (id, created_at, updated_at, budget_enabled, name, user_id, built_in, "
                            + "icon_key) VALUES (7, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, TRUE, 'Pic', 1, FALSE, 'tag')",
                    "INSERT INTO transactions VALUES (1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1.00, 'x', "
                            + "CURRENT_DATE, 'EXPENSE', 2, 1)",
                    "INSERT INTO budgets VALUES (1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1, 10.00, 2026, 1, 2)")) {
                assertThatThrownBy(() -> statement.executeUpdate(invalid)).isInstanceOf(SQLException.class);
            }

            statement.executeUpdate("INSERT INTO transactions VALUES (1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, "
                    + "1.00, 'x', CURRENT_DATE, 'EXPENSE', 1, 1)");
            statement.executeUpdate("INSERT INTO budgets VALUES (1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, "
                    + "1, 10.00, 2026, 3, 1)");
            assertThatThrownBy(() -> statement.executeUpdate("DELETE FROM categories WHERE id = 1"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> statement.executeUpdate("DELETE FROM categories WHERE id = 3"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> statement.executeUpdate("UPDATE transactions SET user_id = 2 WHERE id = 1"))
                    .isInstanceOf(SQLException.class);
            assertThat(statement.executeUpdate("DELETE FROM categories WHERE id = 4")).isEqualTo(1);
        }
    }

    // ------------------------------------------------------------------ helpers

    private static String assertPreflightFailsWithoutChanges(String url, String... expectedProblems)
            throws SQLException {
        Map<String, List<String>> before = snapshot(url);
        Flyway flyway = flyway(url, "latest");

        Throwable failure = org.assertj.core.api.Assertions.catchThrowable(flyway::migrate);

        assertThat(failure).isInstanceOf(FlywayException.class);
        String message = org.assertj.core.util.Throwables.getRootCause(failure).getMessage();
        assertThat(message).startsWith("V6 preflight failed; no schema or data changes were made");
        for (String problem : expectedProblems) {
            assertThat(message).contains(problem);
        }
        assertThat(snapshot(url)).isEqualTo(before);
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            assertThat(columns(connection.getMetaData(), "categories"))
                    .doesNotContainKeys("normalized_name", "built_in", "icon_key");
        }
        return message;
    }

    private static Flyway flyway(String url, String target) {
        return Flyway.configure().dataSource(url, "sa", "")
                .locations("classpath:db/migration").target(target).load();
    }

    private static String databaseUrl(String name) {
        return "jdbc:h2:mem:v6_" + name + "_" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE"
                + ";NON_KEYWORDS=MONTH,YEAR;DB_CLOSE_DELAY=-1";
    }

    private static void execute(String url, String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static void insertUser(Statement statement, long id, String email) throws SQLException {
        statement.executeUpdate("INSERT INTO users (id, created_at, updated_at, first_name, last_name, display_name, "
                + "email, password_hash) VALUES (" + id + ", CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'F', 'L', 'F L', '"
                + email + "', 'test-hash')");
    }

    private static void insertCategory(Statement statement, long id, long userId, String name, boolean budgetEnabled,
                                       String createdAt, String updatedAt) throws SQLException {
        statement.executeUpdate("INSERT INTO categories (id, created_at, updated_at, budget_enabled, name, user_id) "
                + "VALUES (" + id + ", '" + createdAt + "', '" + updatedAt + "', " + budgetEnabled + ", '" + name
                + "', " + userId + ")");
    }

    private static String category(long id, long userId, String name, String normalized, String icon) {
        return "INSERT INTO categories (id, created_at, updated_at, budget_enabled, name, user_id, normalized_name, "
                + "built_in, icon_key) VALUES (" + id + ", CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, TRUE, '" + name
                + "', " + userId + ", '" + normalized + "', FALSE, '" + icon + "')";
    }

    private static List<String> metadata(String url) throws SQLException {
        List<String> rows = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT id, normalized_name, built_in, icon_key FROM categories ORDER BY id")) {
            while (result.next()) {
                rows.add(result.getLong(1) + " " + result.getString(2) + " built_in=" + result.getBoolean(3)
                        + " " + result.getString(4));
            }
        }
        return rows;
    }

    /** Every pre-V6 column of every table that V6 touches or references. */
    private static Map<String, List<String>> snapshot(String url) throws SQLException {
        Map<String, String> tables = new LinkedHashMap<>();
        tables.put("users", "*");
        tables.put("categories", "id, created_at, updated_at, budget_enabled, name, user_id");
        tables.put("transactions", "*");
        tables.put("budgets", "*");
        Map<String, List<String>> result = new LinkedHashMap<>();
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            for (Map.Entry<String, String> table : tables.entrySet()) {
                List<String> values = new ArrayList<>();
                try (ResultSet rows = statement.executeQuery(
                        "SELECT " + table.getValue() + " FROM " + table.getKey() + " ORDER BY id")) {
                    while (rows.next()) {
                        for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) {
                            values.add(rows.getMetaData().getColumnName(i).toLowerCase(Locale.ROOT) + "="
                                    + rows.getString(i));
                        }
                    }
                }
                result.put(table.getKey(), values);
            }
        }
        return result;
    }

    private static Map<String, String> columns(DatabaseMetaData metadata, String table) throws SQLException {
        Map<String, String> result = new LinkedHashMap<>();
        try (ResultSet rows = metadata.getColumns(null, null, table, null)) {
            while (rows.next()) {
                String size = rows.getString("TYPE_NAME").startsWith("CHARACTER VARYING")
                        || rows.getString("TYPE_NAME").equals("VARCHAR") ? "(" + rows.getInt("COLUMN_SIZE") + ")" : "";
                String type = rows.getString("TYPE_NAME").equals("CHARACTER VARYING") ? "VARCHAR"
                        : rows.getString("TYPE_NAME");
                result.put(rows.getString("COLUMN_NAME").toLowerCase(Locale.ROOT), type + size
                        + (rows.getInt("NULLABLE") == DatabaseMetaData.columnNullable ? " NULL" : " NOT NULL"));
            }
        }
        return result;
    }

    private static Map<String, List<String>> indexColumns(DatabaseMetaData metadata, String table)
            throws SQLException {
        Map<String, List<String>> result = new TreeMap<>();
        try (ResultSet rows = metadata.getIndexInfo(null, null, table, false, false)) {
            while (rows.next()) {
                if (rows.getString("COLUMN_NAME") != null) {
                    result.computeIfAbsent(rows.getString("INDEX_NAME").toLowerCase(Locale.ROOT),
                            key -> new ArrayList<>()).add(rows.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                }
            }
        }
        return result;
    }

    private static List<List<String>> uniqueIndexColumns(DatabaseMetaData metadata, String table)
            throws SQLException {
        Map<String, List<String>> unique = new TreeMap<>();
        try (ResultSet rows = metadata.getIndexInfo(null, null, table, true, false)) {
            while (rows.next()) {
                if (rows.getString("COLUMN_NAME") != null) {
                    unique.computeIfAbsent(rows.getString("INDEX_NAME"), key -> new ArrayList<>())
                            .add(rows.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                }
            }
        }
        return new ArrayList<>(unique.values());
    }

    private static List<String> foreignKeys(DatabaseMetaData metadata, String table) throws SQLException {
        Map<String, String[]> keys = new LinkedHashMap<>();
        try (ResultSet rows = metadata.getImportedKeys(null, null, table)) {
            while (rows.next()) {
                String referenced = rows.getString("PKTABLE_NAME").toLowerCase(Locale.ROOT);
                String[] key = keys.computeIfAbsent(rows.getString("FK_NAME").toLowerCase(Locale.ROOT),
                        name -> new String[] {"", "", referenced});
                key[0] += (key[0].isEmpty() ? "" : ",") + rows.getString("FKCOLUMN_NAME").toLowerCase(Locale.ROOT);
                key[1] += (key[1].isEmpty() ? "" : ",") + rows.getString("PKCOLUMN_NAME").toLowerCase(Locale.ROOT);
            }
        }
        List<String> result = new ArrayList<>();
        keys.forEach((name, key) -> result.add(name + " (" + key[0] + ") -> " + key[2] + " (" + key[1] + ")"));
        return result;
    }
}
