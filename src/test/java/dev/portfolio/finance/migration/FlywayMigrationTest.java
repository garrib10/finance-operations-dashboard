package dev.portfolio.finance.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class FlywayMigrationTest {

    @Test
    void shouldApplyAllMigrationsToCleanDatabase() throws SQLException {
        String databaseUrl = databaseUrl("clean");
        Flyway flyway = configureFlyway(databaseUrl, false);

        flyway.migrate();

        assertThat(currentVersion(flyway)).isEqualTo("6");
        assertThat(rowCount(databaseUrl, "users")).isZero();
        assertThat(rowCount(databaseUrl, "categories")).isZero();
        assertThat(rowCount(databaseUrl, "transactions")).isZero();
        assertThat(rowCount(databaseUrl, "budgets")).isZero();
        assertThat(rowCount(databaseUrl, "refresh_sessions")).isZero();
        assertThat(rowCount(databaseUrl, "refresh_tokens")).isZero();
        assertThat(historyCount(databaseUrl, "1", "SQL")).isEqualTo(1);
        assertThat(historyCount(databaseUrl, "2", "SQL")).isEqualTo(1);
        assertThat(historyCount(databaseUrl, "3", "SQL")).isEqualTo(1);
        assertThat(historyCount(databaseUrl, "4", "SQL")).isEqualTo(1);
        assertThat(historyCount(databaseUrl, "5", "SQL")).isEqualTo(1);
        assertThat(historyCount(databaseUrl, "6", "JDBC")).isEqualTo(1);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(transactionLookupIndexCount(databaseUrl)).isEqualTo(1);
    }

    @Test
    void shouldBaselineExistingSchemaWithoutLosingData() throws SQLException {
        String databaseUrl = databaseUrl("existing");

        createExistingSchema(databaseUrl);
        insertExistingUser(databaseUrl);

        Flyway flyway = configureFlyway(databaseUrl, true);
        flyway.migrate();

        assertThat(currentVersion(flyway)).isEqualTo("6");
        assertThat(rowCount(databaseUrl, "users")).isEqualTo(1);
        assertThat(rowCount(databaseUrl, "refresh_sessions")).isZero();
        assertThat(historyCount(databaseUrl, "1", "BASELINE")).isEqualTo(1);
        assertThat(historyCount(databaseUrl, "2", "SQL")).isEqualTo(1);
        assertThat(historyCount(databaseUrl, "3", "SQL")).isEqualTo(1);
        assertThat(historyCount(databaseUrl, "4", "SQL")).isEqualTo(1);
        assertThat(historyCount(databaseUrl, "5", "SQL")).isEqualTo(1);
        assertThat(historyCount(databaseUrl, "6", "JDBC")).isEqualTo(1);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(transactionLookupIndexCount(databaseUrl)).isEqualTo(1);
    }

    @Test
    void shouldFailWhenMigrationContainsInvalidSql() {
        String databaseUrl = databaseUrl("invalid");

        Flyway flyway = Flyway.configure()
                .dataSource(databaseUrl, "sa", "")
                .locations("classpath:db/invalid-migration")
                .load();

        assertThatThrownBy(flyway::migrate)
                .isInstanceOf(FlywayException.class);
    }

    @Test
    void shouldUpgradePopulatedV2PreservingDataAndEnforcingDefaults() throws SQLException {
        String url = databaseUrl("upgrade");
        Flyway.configure().dataSource(url, "sa", "")
                .locations("classpath:db/migration").target("2").load().migrate();
        insertExistingUser(url);
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE users SET first_name = '  Flyway  ', last_name = ' Test  '");
            statement.executeUpdate("INSERT INTO categories VALUES (1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, TRUE, 'Food', 1)");
            statement.executeUpdate("INSERT INTO transactions VALUES (1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 12.50, 'Lunch', CURRENT_DATE, 'EXPENSE', 1, 1)");
            statement.executeUpdate("INSERT INTO budgets VALUES (1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 9, 100.00, 2026, 1, 1)");
            statement.executeUpdate("INSERT INTO users (first_name,last_name,email,password_hash,created_at,updated_at) VALUES (' ', ' ', 'blank@example.com', 'blank-hash', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
            statement.executeUpdate("INSERT INTO users (first_name,last_name,email,password_hash,created_at,updated_at) VALUES (REPEAT('a',100), REPEAT('b',100), 'long@example.com', 'long-hash', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
            String original;
            try (ResultSet row = statement.executeQuery("SELECT CONCAT(id, email, first_name, last_name, password_hash, created_at, updated_at) FROM users WHERE id = 1")) {
                row.next();
                original = row.getString(1);
            }
            Flyway flyway = configureFlyway(url, false);
            flyway.migrate();
            assertThat(currentVersion(flyway)).isEqualTo("6");
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            assertThat(historyCount(url, "3", "SQL")).isEqualTo(1);
            try (ResultSet rows = statement.executeQuery("SELECT *, CONCAT(id, email, first_name, last_name, password_hash, created_at, updated_at) AS original FROM users ORDER BY id")) {
                rows.next();
                assertThat(rows.getString("original")).isEqualTo(original);
                assertThat(rows.getString("display_name")).isEqualTo("Flyway Test");
                assertThat(rows.getString("date_format")).isEqualTo("MEDIUM");
                assertThat(rows.getInt("transaction_page_size")).isEqualTo(10);
                rows.next();
                assertThat(rows.getString("display_name")).isEqualTo("Account");
                rows.next();
                assertThat(rows.getString("display_name")).isEqualTo("a".repeat(100));
            }
            assertThat(rowCount(url, "users")).isEqualTo(3);
            assertThat(rowCount(url, "categories")).isEqualTo(1);
            assertThat(rowCount(url, "transactions")).isEqualTo(1);
            assertThat(rowCount(url, "budgets")).isEqualTo(1);
            statement.executeUpdate("INSERT INTO users (first_name,last_name,display_name,email,password_hash,created_at,updated_at) VALUES ('New', 'User', 'New User', 'new@example.com', 'hash', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
            try (ResultSet row = statement.executeQuery("SELECT date_format, transaction_page_size FROM users WHERE email = 'new@example.com'")) {
                row.next();
                assertThat(row.getString(1)).isEqualTo("MEDIUM");
                assertThat(row.getInt(2)).isEqualTo(10);
            }
            statement.executeUpdate("UPDATE users SET date_format = 'ISO', transaction_page_size = 25 WHERE id = 1");
            statement.executeUpdate("UPDATE users SET transaction_page_size = 50 WHERE id = 1");
            assertThatThrownBy(() -> statement.executeUpdate("UPDATE users SET display_name = NULL WHERE id = 1")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> statement.executeUpdate("UPDATE users SET date_format = 'OTHER' WHERE id = 1")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> statement.executeUpdate("UPDATE users SET transaction_page_size = 11 WHERE id = 1")).isInstanceOf(SQLException.class);
        }
    }

    @Test
    void upgradesPopulatedV3WithoutChangingExistingData() throws SQLException {
        String url = databaseUrl("photo_upgrade");
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration")
                .target("3").load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO users (id,created_at,updated_at,first_name,last_name,display_name,email,password_hash,date_format,transaction_page_size) VALUES (1,'2026-01-01 12:00:00.123456','2026-02-01 12:00:00.654321','First','Last','Display','photo@example.com','test-hash','ISO',25)");
            statement.executeUpdate("INSERT INTO categories VALUES (1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, TRUE, 'Food', 1)");
            statement.executeUpdate("INSERT INTO transactions VALUES (1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 12.50, 'Lunch', CURRENT_DATE, 'EXPENSE', 1, 1)");
            statement.executeUpdate("INSERT INTO budgets VALUES (1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 9, 100.00, 2026, 1, 1)");
        }
        var before = snapshot(url);
        Flyway flyway = configureFlyway(url, false, "5");
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(2);
        assertThat(currentVersion(flyway)).isEqualTo("5");
        assertThat(historyCount(url, "4", "SQL")).isEqualTo(1);
        assertThat(historyCount(url, "5", "SQL")).isEqualTo(1);
        assertThat(snapshot(url)).isEqualTo(before);
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT profile_photo_key FROM users")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isNull();
            assertThat(rows.getMetaData().getPrecision(1)).isEqualTo(255);
            assertThat(rows.getMetaData().isNullable(1)).isEqualTo(java.sql.ResultSetMetaData.columnNullable);
        }
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @Test
    void upgradesPopulatedV4ToV5WithoutChangingExistingData() throws SQLException {
        String url = databaseUrl("refresh_upgrade");
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration")
                .target("4").load().migrate();
        String photoKey = "fintrack/test/profile-photos/0f8fad5b-d9cb-469f-a165-70867728950e";
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO users (id,created_at,updated_at,first_name,last_name,display_name,email,password_hash,date_format,transaction_page_size,profile_photo_key) VALUES (1,'2026-01-01 12:00:00.123456','2026-02-01 12:00:00.654321','First','Last','Display','photo@example.com','test-hash-one','ISO',25,'" + photoKey + "')");
            statement.executeUpdate("INSERT INTO users (id,created_at,updated_at,first_name,last_name,display_name,email,password_hash,date_format,transaction_page_size,profile_photo_key) VALUES (2,'2026-03-01 08:00:00.000001','2026-03-02 08:00:00.999999','No','Photo','No Photo','nophoto@example.com','test-hash-two','MEDIUM',50,NULL)");
            statement.executeUpdate("INSERT INTO categories VALUES (1, '2026-01-02 00:00:00.000001', '2026-01-02 00:00:00.000002', TRUE, 'Food', 1)");
            statement.executeUpdate("INSERT INTO categories VALUES (2, '2026-03-02 00:00:00.000001', '2026-03-02 00:00:00.000002', FALSE, 'Rent', 2)");
            statement.executeUpdate("INSERT INTO transactions VALUES (1, '2026-01-03 00:00:00.5', '2026-01-03 00:00:00.5', 12.50, 'Lunch', '2026-01-03', 'EXPENSE', 1, 1)");
            statement.executeUpdate("INSERT INTO transactions VALUES (2, '2026-03-03 00:00:00.5', '2026-03-03 00:00:00.5', 900.00, 'Pay', '2026-03-03', 'INCOME', 2, 2)");
            statement.executeUpdate("INSERT INTO budgets VALUES (1, '2026-01-04 00:00:00', '2026-01-04 00:00:00', 9, 100.00, 2026, 1, 1)");
        }
        var before = snapshot(url, java.util.Set.of());

        Flyway flyway = configureFlyway(url, false, "5");
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(currentVersion(flyway)).isEqualTo("5");
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(historyCount(url, "5", "SQL")).isEqualTo(1);
        assertThat(snapshot(url, java.util.Set.of())).isEqualTo(before);
        assertThat(before.get("users")).contains("password_hash=test-hash-one", "password_hash=test-hash-two",
                "profile_photo_key=" + photoKey, "profile_photo_key=null", "date_format=ISO",
                "transaction_page_size=25", "display_name=Display");
        assertThat(rowCount(url, "users")).isEqualTo(2);
        assertThat(rowCount(url, "refresh_sessions")).isZero();
        assertThat(rowCount(url, "refresh_tokens")).isZero();

        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(currentVersion(flyway)).isEqualTo("5");
        assertThat(snapshot(url, java.util.Set.of())).isEqualTo(before);
    }

    @Test
    void v5CreatesRefreshTablesWithRequiredColumnsKeysAndIndexes() throws SQLException {
        String url = databaseUrl("refresh_schema");
        configureFlyway(url, false).migrate();

        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            var metadata = connection.getMetaData();

            assertThat(columns(metadata, "refresh_sessions")).containsExactly(
                    "id CHAR(36) NOT NULL",
                    "user_id BIGINT NOT NULL",
                    "created_at TIMESTAMP(6) NOT NULL",
                    "expires_at TIMESTAMP(6) NOT NULL",
                    "revoked_at TIMESTAMP(6) NULL",
                    "revocation_reason VARCHAR(24) NULL");
            assertThat(columns(metadata, "refresh_tokens")).containsExactly(
                    "id BIGINT NOT NULL",
                    "session_id CHAR(36) NOT NULL",
                    "token_hash BINARY(32) NOT NULL",
                    "created_at TIMESTAMP(6) NOT NULL",
                    "consumed_at TIMESTAMP(6) NULL");

            assertThat(primaryKey(metadata, "refresh_sessions")).containsExactly("id");
            assertThat(primaryKey(metadata, "refresh_tokens")).containsExactly("id");

            var sessionIndexes = indexes(metadata, "refresh_sessions", false);
            assertThat(sessionIndexes).containsEntry("idx_refresh_sessions_user_revoked",
                    java.util.List.of("user_id", "revoked_at"));
            assertThat(sessionIndexes).containsEntry("idx_refresh_sessions_expires_at",
                    java.util.List.of("expires_at"));
            assertThat(indexes(metadata, "refresh_tokens", false)).containsEntry(
                    "idx_refresh_tokens_session_id", java.util.List.of("session_id"));
            assertThat(indexes(metadata, "refresh_tokens", true).values())
                    .contains(java.util.List.of("token_hash"));

            assertThat(foreignKeys(metadata, "refresh_sessions")).containsExactly(
                    "user_id -> users.id delete=" + java.sql.DatabaseMetaData.importedKeyNoAction);
            assertThat(foreignKeys(metadata, "refresh_tokens")).containsExactly(
                    "session_id -> refresh_sessions.id delete=" + java.sql.DatabaseMetaData.importedKeyCascade);
        }
    }

    @Test
    void v5EnforcesSessionAndTokenIntegrity() throws SQLException {
        String url = databaseUrl("refresh_constraints");
        configureFlyway(url, false).migrate();
        String s1 = "11111111-1111-4111-8111-111111111111";
        String s2 = "22222222-2222-4222-8222-222222222222";
        String hashA = "X'" + "aa".repeat(32) + "'";
        String hashB = "X'" + "bb".repeat(32) + "'";
        String hashC = "X'" + "cc".repeat(32) + "'";

        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            insertUserRow(statement, 1, "owner@example.com");
            statement.executeUpdate(sessionRow(s1, 1, "'2026-09-27 12:00:00.000001'", "'2026-10-27 12:00:00.000001'", "NULL", "NULL"));
            statement.executeUpdate(sessionRow(s2, 1, "'2026-09-27 12:00:00'", "'2026-10-27 12:00:00'", "'2026-09-28 12:00:00'", "'LOGOUT'"));
            int i = 3;
            for (String reason : java.util.List.of("PASSWORD_CHANGE", "REUSE_DETECTED")) {
                statement.executeUpdate(sessionRow(String.valueOf(i).repeat(8) + "-0000-4000-8000-000000000000", 1,
                        "'2026-09-27 12:00:00'", "'2026-10-27 12:00:00'", "'2026-09-28 12:00:00'", "'" + reason + "'"));
                i++;
            }

            String bad = "99999999-9999-4999-8999-999999999999";
            for (String invalid : java.util.List.of(
                    sessionRow(bad, 1, "'2026-09-27 12:00:00'", "'2026-09-27 12:00:00'", "NULL", "NULL"),
                    sessionRow(bad, 1, "'2026-09-27 12:00:00'", "'2026-09-26 12:00:00'", "NULL", "NULL"),
                    sessionRow(bad, 1, "'2026-09-27 12:00:00'", "'2026-10-27 12:00:00'", "'2026-09-28 12:00:00'", "NULL"),
                    sessionRow(bad, 1, "'2026-09-27 12:00:00'", "'2026-10-27 12:00:00'", "NULL", "'LOGOUT'"),
                    sessionRow(bad, 1, "'2026-09-27 12:00:00'", "'2026-10-27 12:00:00'", "'2026-09-28 12:00:00'", "'EXPIRED'"),
                    sessionRow(bad, 1, "'2026-09-27 12:00:00'", "'2026-10-27 12:00:00'", "'2026-09-28 12:00:00'", "'logout'"),
                    sessionRow(bad, 999, "'2026-09-27 12:00:00'", "'2026-10-27 12:00:00'", "NULL", "NULL"),
                    sessionRow(bad, 1, "NULL", "'2026-10-27 12:00:00'", "NULL", "NULL"),
                    sessionRow(s1, 1, "'2026-09-27 12:00:00'", "'2026-10-27 12:00:00'", "NULL", "NULL"))) {
                assertThatThrownBy(() -> statement.executeUpdate(invalid)).isInstanceOf(SQLException.class);
            }
            assertThatThrownBy(() -> statement.executeUpdate(
                    "UPDATE refresh_sessions SET revocation_reason = 'LOGOUT' WHERE id = '" + s1 + "'"))
                    .isInstanceOf(SQLException.class);

            statement.executeUpdate(tokenRow(s1, hashA, "NULL"));
            statement.executeUpdate(tokenRow(s1, hashB, "'2026-09-27 12:05:00'"));
            statement.executeUpdate(tokenRow(s2, hashC, "NULL"));
            for (String invalid : java.util.List.of(
                    tokenRow(s1, hashA, "NULL"),
                    tokenRow(s2, hashA, "NULL"),
                    tokenRow(bad, "X'" + "dd".repeat(32) + "'", "NULL"),
                    tokenRow(s1, "NULL", "NULL"),
                    tokenRow("NULL", "X'" + "ee".repeat(32) + "'", "NULL"))) {
                assertThatThrownBy(() -> statement.executeUpdate(invalid)).isInstanceOf(SQLException.class);
            }
            try (ResultSet rows = statement.executeQuery("SELECT token_hash FROM refresh_tokens WHERE token_hash = " + hashA)) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getBytes(1)).hasSize(32);
                assertThat(rows.next()).isFalse();
            }

            assertThatThrownBy(() -> statement.executeUpdate("DELETE FROM users WHERE id = 1"))
                    .isInstanceOf(SQLException.class);

            statement.executeUpdate("DELETE FROM refresh_sessions WHERE id = '" + s1 + "'");
            assertThat(rowCount(url, "refresh_tokens")).isEqualTo(1);
            try (ResultSet rows = statement.executeQuery("SELECT session_id FROM refresh_tokens")) {
                rows.next();
                assertThat(rows.getString(1)).isEqualTo(s2);
            }
            assertThat(rowCount(url, "users")).isEqualTo(1);
        }
    }

    @Test
    void v5HasNoBackfillOrDataStatements() throws Exception {
        String script = new ClassPathResource("db/migration/V5__add_refresh_sessions.sql")
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8)
                .toUpperCase(java.util.Locale.ROOT);

        assertThat(script).doesNotContain("INSERT", "UPDATE ", "DELETE FROM", "ALTER TABLE USERS", "DROP");
        assertThat(script).contains("CREATE TABLE REFRESH_SESSIONS", "CREATE TABLE REFRESH_TOKENS");
    }

    private static void insertUserRow(Statement statement, long id, String email) throws SQLException {
        statement.executeUpdate("INSERT INTO users (id,created_at,updated_at,first_name,last_name,display_name,email,password_hash) VALUES ("
                + id + ", CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'F', 'L', 'F L', '" + email + "', 'test-hash')");
    }

    private static String sessionRow(String id, long userId, String createdAt, String expiresAt,
                                     String revokedAt, String reason) {
        return "INSERT INTO refresh_sessions (id,user_id,created_at,expires_at,revoked_at,revocation_reason) VALUES ('"
                + id + "', " + userId + ", " + createdAt + ", " + expiresAt + ", " + revokedAt + ", " + reason + ")";
    }

    private static String tokenRow(String sessionId, String hash, String consumedAt) {
        String session = sessionId.equals("NULL") ? "NULL" : "'" + sessionId + "'";
        return "INSERT INTO refresh_tokens (session_id,token_hash,created_at,consumed_at) VALUES ("
                + session + ", " + hash + ", '2026-09-27 12:00:00', " + consumedAt + ")";
    }

    private static java.util.List<String> columns(java.sql.DatabaseMetaData metadata, String table)
            throws SQLException {
        var result = new java.util.ArrayList<String>();
        try (ResultSet rows = metadata.getColumns(null, null, table, null)) {
            while (rows.next()) {
                var type = java.sql.JDBCType.valueOf(rows.getInt("DATA_TYPE"));
                String size = switch (type) {
                    case CHAR, VARCHAR, BINARY -> "(" + rows.getInt("COLUMN_SIZE") + ")";
                    case TIMESTAMP -> "(" + rows.getInt("DECIMAL_DIGITS") + ")";
                    default -> "";
                };
                boolean nullable = rows.getInt("NULLABLE") == java.sql.DatabaseMetaData.columnNullable;
                result.add(rows.getString("COLUMN_NAME").toLowerCase(java.util.Locale.ROOT) + " "
                        + type.getName() + size + (nullable ? " NULL" : " NOT NULL"));
            }
        }
        return result;
    }

    private static java.util.List<String> primaryKey(java.sql.DatabaseMetaData metadata, String table)
            throws SQLException {
        var result = new java.util.ArrayList<String>();
        try (ResultSet rows = metadata.getPrimaryKeys(null, null, table)) {
            while (rows.next()) {
                result.add(rows.getString("COLUMN_NAME").toLowerCase(java.util.Locale.ROOT));
            }
        }
        return result;
    }

    private static java.util.Map<String, java.util.List<String>> indexes(
            java.sql.DatabaseMetaData metadata, String table, boolean uniqueOnly) throws SQLException {
        var result = new java.util.TreeMap<String, java.util.List<String>>();
        try (ResultSet rows = metadata.getIndexInfo(null, null, table, uniqueOnly, false)) {
            while (rows.next()) {
                if (rows.getString("COLUMN_NAME") == null) {
                    continue;
                }
                result.computeIfAbsent(rows.getString("INDEX_NAME").toLowerCase(java.util.Locale.ROOT),
                                key -> new java.util.ArrayList<>())
                        .add(rows.getString("COLUMN_NAME").toLowerCase(java.util.Locale.ROOT));
            }
        }
        return result;
    }

    private static java.util.List<String> foreignKeys(java.sql.DatabaseMetaData metadata, String table)
            throws SQLException {
        var result = new java.util.ArrayList<String>();
        try (ResultSet rows = metadata.getImportedKeys(null, null, table)) {
            while (rows.next()) {
                result.add(rows.getString("FKCOLUMN_NAME").toLowerCase(java.util.Locale.ROOT) + " -> "
                        + rows.getString("PKTABLE_NAME").toLowerCase(java.util.Locale.ROOT) + "."
                        + rows.getString("PKCOLUMN_NAME").toLowerCase(java.util.Locale.ROOT)
                        + " delete=" + rows.getShort("DELETE_RULE"));
            }
        }
        return result;
    }

    private java.util.Map<String, java.util.List<String>> snapshot(String url) throws SQLException {
        return snapshot(url, java.util.Set.of("profile_photo_key"));
    }

    private java.util.Map<String, java.util.List<String>> snapshot(String url, java.util.Set<String> excluded)
            throws SQLException {
        var result = new java.util.LinkedHashMap<String, java.util.List<String>>();
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            for (String table : java.util.List.of("users", "categories", "transactions", "budgets")) {
                var values = new java.util.ArrayList<String>();
                try (var rows = statement.executeQuery("SELECT * FROM " + table + " ORDER BY id")) {
                    while (rows.next()) {
                        for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) {
                            if (!excluded.contains(rows.getMetaData().getColumnName(i).toLowerCase(java.util.Locale.ROOT))) {
                                values.add(rows.getMetaData().getColumnName(i) + "=" + rows.getString(i));
                            }
                        }
                    }
                }
                result.put(table, values);
            }
        }
        return result;
    }

    private Flyway configureFlyway(
            String databaseUrl,
            boolean baselineOnMigrate
    ) {
        return configureFlyway(databaseUrl, baselineOnMigrate, "latest");
    }

    private Flyway configureFlyway(
            String databaseUrl,
            boolean baselineOnMigrate,
            String target
    ) {
        return Flyway.configure()
                .dataSource(databaseUrl, "sa", "")
                .locations("classpath:db/migration")
                .baselineOnMigrate(baselineOnMigrate)
                .baselineVersion(MigrationVersion.fromVersion("1"))
                .target(target)
                .load();
    }

    private String databaseUrl(String name) {
        return "jdbc:h2:mem:flyway_"
                + name
                + "_"
                + UUID.randomUUID()
                + ";MODE=MySQL"
                + ";DATABASE_TO_LOWER=TRUE"
                + ";CASE_INSENSITIVE_IDENTIFIERS=TRUE"
                + ";NON_KEYWORDS=MONTH,YEAR"
                + ";DB_CLOSE_DELAY=-1";
    }

    private void createExistingSchema(String databaseUrl) throws SQLException {
        try (Connection connection =
                     DriverManager.getConnection(databaseUrl, "sa", "")) {

            ScriptUtils.executeSqlScript(
                    connection,
                    new EncodedResource(
                            new ClassPathResource(
                                    "db/migration/V1__baseline_schema.sql"
                            )
                    )
            );
        }
    }

    private void insertExistingUser(String databaseUrl) throws SQLException {
        String sql = """
                INSERT INTO users (
                    first_name,
                    last_name,
                    email,
                    password_hash,
                    created_at,
                    updated_at
                )
                VALUES (
                    'Flyway',
                    'Test',
                    'flyway-test@example.com',
                    'test-only-password-hash',
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP
                )
                """;

        try (
                Connection connection =
                        DriverManager.getConnection(databaseUrl, "sa", "");
                Statement statement = connection.createStatement()
        ) {
            statement.executeUpdate(sql);
        }
    }

    private String currentVersion(Flyway flyway) {
        return flyway.info()
                .current()
                .getVersion()
                .getVersion();
    }

    private long rowCount(
            String databaseUrl,
            String tableName
    ) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + tableName;

        try (
                Connection connection =
                        DriverManager.getConnection(databaseUrl, "sa", "");
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)
        ) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private long historyCount(
            String databaseUrl,
            String version,
            String type
    ) throws SQLException {
        String sql = """
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE version = '%s'
                  AND type = '%s'
                  AND success = TRUE
                """.formatted(version, type);

        try (
                Connection connection =
                        DriverManager.getConnection(databaseUrl, "sa", "");
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)
        ) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private long transactionLookupIndexCount(
            String databaseUrl
    ) throws SQLException {
        String sql = """
                SELECT COUNT(*)
                FROM information_schema.indexes
                WHERE UPPER(index_name) =
                    'IDX_TRANSACTIONS_USER_DATE'
                """;

        try (
                Connection connection =
                        DriverManager.getConnection(databaseUrl, "sa", "");
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)
        ) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }
}
