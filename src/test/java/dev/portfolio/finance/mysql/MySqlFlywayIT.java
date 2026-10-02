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
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

/** Flyway V1–V6 startup and V5 constraints on the pinned MySQL server (not H2). V6: CategoryV6MySqlIT. */
class MySqlFlywayIT extends MySqlIntegrationTestBase {

    @Autowired private Flyway flyway;
    @Value("${spring.jpa.hibernate.ddl-auto}") private String ddlAuto;

    @Test
    void applicationStartsOnMysqlAtLatestVersionWithHibernateValidation() {
        assertThat(jdbc.queryForObject("SELECT VERSION()", String.class)).startsWith("8.4.6");
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("6");
        assertThat(flyway.info().pending()).isEmpty();
        // The context only starts if Hibernate's MySQL schema validation accepted every entity.
        assertThat(ddlAuto).isEqualTo("validate");
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @Test
    void populatedV4DatabaseUpgradesWithoutChangingExistingData() throws Exception {
        String schema = "upgrade_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        try (Connection root = rootConnection("fintrack"); Statement statement = root.createStatement()) {
            statement.executeUpdate("CREATE DATABASE " + schema
                    + " DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
        String url = MYSQL.getJdbcUrl().replace("/fintrack", "/" + schema);
        Flyway.configure().dataSource(url, "root", MYSQL.getPassword())
                .locations("classpath:db/migration").target("4").load().migrate();
        try (Connection connection = rootConnection(schema); Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO users (id,created_at,updated_at,first_name,last_name,display_name,"
                    + "email,password_hash,date_format,transaction_page_size,profile_photo_key) VALUES "
                    + "(1,'2026-01-01 12:00:00.123456','2026-02-01 12:00:00.654321','First','Last','Display',"
                    + "'photo@example.com','test-hash-one','ISO',25,'fintrack/test/profile-photos/key-one'),"
                    + "(2,'2026-03-01 08:00:00.000001','2026-03-02 08:00:00.999999','No','Photo','No Photo',"
                    + "'nophoto@example.com','test-hash-two','MEDIUM',50,NULL)");
            statement.executeUpdate("INSERT INTO categories VALUES (1,'2026-01-02 00:00:00.000001',"
                    + "'2026-01-02 00:00:00.000002',b'1','Food',1)");
            statement.executeUpdate("INSERT INTO transactions VALUES (1,'2026-01-03 00:00:00.5',"
                    + "'2026-01-03 00:00:00.5',12.50,'Lunch','2026-01-03','EXPENSE',1,1)");
            statement.executeUpdate("INSERT INTO budgets VALUES (1,'2026-01-04 00:00:00','2026-01-04 00:00:00',"
                    + "9,100.00,2026,1,1)");
        }
        Map<String, List<String>> before = snapshot(schema);

        Flyway upgrade = Flyway.configure().dataSource(url, "root", MYSQL.getPassword())
                .locations("classpath:db/migration").target("5").load();
        assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(upgrade.info().current().getVersion().getVersion()).isEqualTo("5");
        assertThat(snapshot(schema)).isEqualTo(before);
        assertThat(before.get("users")).contains("password_hash=test-hash-one",
                "profile_photo_key=fintrack/test/profile-photos/key-one", "profile_photo_key=null");
        try (Connection connection = rootConnection(schema); Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT (SELECT COUNT(*) FROM refresh_sessions) "
                     + "+ (SELECT COUNT(*) FROM refresh_tokens)")) {
            rows.next();
            assertThat(rows.getInt(1)).isZero();
        }
        assertThat(upgrade.migrate().migrationsExecuted).isZero();
        assertThat(snapshot(schema)).isEqualTo(before);
    }

    @Test
    void mysqlEnforcesV5ConstraintsCascadeAndIndexes() throws Exception {
        long userId = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) + 1000 FROM users", Long.class);
        jdbc.update("INSERT INTO users (id,created_at,updated_at,first_name,last_name,display_name,email,password_hash) "
                + "VALUES (?, NOW(6), NOW(6), 'F', 'L', 'F L', ?, 'hash')", userId, "constraint-" + userId + "@example.com");
        String kept = UUID.randomUUID().toString();
        String doomed = UUID.randomUUID().toString();
        insertSession(kept, userId, "'2026-09-27 12:00:00'", "'2026-10-27 12:00:00'", "NULL", "NULL");
        insertSession(doomed, userId, "'2026-09-27 12:00:00'", "'2026-10-27 12:00:00'",
                "'2026-09-28 12:00:00'", "'REUSE_DETECTED'");
        String hash = "X'" + "ab".repeat(32) + "'";
        jdbc.update("INSERT INTO refresh_tokens (session_id, token_hash, created_at) VALUES (?, " + hash
                + ", '2026-09-27 12:00:00')", doomed);

        String bad = UUID.randomUUID().toString();
        for (String[] invalid : List.of(
                new String[] {"'2026-09-27 12:00:00'", "'2026-09-27 12:00:00'", "NULL", "NULL"},
                new String[] {"'2026-09-27 12:00:00'", "'2026-10-27 12:00:00'", "'2026-09-28 12:00:00'", "NULL"},
                new String[] {"'2026-09-27 12:00:00'", "'2026-10-27 12:00:00'", "NULL", "'LOGOUT'"},
                new String[] {"'2026-09-27 12:00:00'", "'2026-10-27 12:00:00'", "'2026-09-28 12:00:00'", "'EXPIRED'"})) {
            assertThatThrownBy(() -> insertSession(bad, userId, invalid[0], invalid[1], invalid[2], invalid[3]))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class);
        }
        assertThatThrownBy(() -> jdbc.update("INSERT INTO refresh_tokens (session_id, token_hash, created_at) "
                + "VALUES (?, " + hash + ", NOW(6))", kept))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM users WHERE id = ?", userId))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        jdbc.update("DELETE FROM refresh_sessions WHERE id = ?", doomed);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE session_id = ?", Long.class,
                doomed)).isZero();

        assertThat(indexColumns("refresh_sessions")).containsEntry("idx_refresh_sessions_user_revoked",
                List.of("user_id", "revoked_at")).containsEntry("idx_refresh_sessions_expires_at", List.of("expires_at"));
        assertThat(indexColumns("refresh_tokens")).containsEntry("idx_refresh_tokens_session_id", List.of("session_id"))
                .containsEntry("uk_refresh_tokens_token_hash", List.of("token_hash"));
        assertThat(jdbc.queryForObject("SELECT COLUMN_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = "
                + "DATABASE() AND TABLE_NAME = 'refresh_tokens' AND COLUMN_NAME = 'token_hash'", String.class))
                .isEqualTo("binary(32)");
        jdbc.update("DELETE FROM refresh_sessions WHERE id = ?", kept);
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
    }

    private void insertSession(String id, long userId, String createdAt, String expiresAt, String revokedAt,
                               String reason) {
        jdbc.update("INSERT INTO refresh_sessions (id,user_id,created_at,expires_at,revoked_at,revocation_reason) "
                + "VALUES (?, ?, " + createdAt + ", " + expiresAt + ", " + revokedAt + ", " + reason + ")", id, userId);
    }

    private Map<String, List<String>> indexColumns(String table) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        jdbc.query("SELECT INDEX_NAME, COLUMN_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() "
                        + "AND TABLE_NAME = ? ORDER BY INDEX_NAME, SEQ_IN_INDEX",
                rs -> {
                    result.computeIfAbsent(rs.getString(1), key -> new ArrayList<>()).add(rs.getString(2));
                }, table);
        return result;
    }

    private static Map<String, List<String>> snapshot(String schema) throws SQLException {
        Map<String, List<String>> result = new LinkedHashMap<>();
        try (Connection connection = rootConnection(schema); Statement statement = connection.createStatement()) {
            for (String table : List.of("users", "categories", "transactions", "budgets")) {
                List<String> values = new ArrayList<>();
                try (ResultSet rows = statement.executeQuery("SELECT * FROM " + table + " ORDER BY id")) {
                    while (rows.next()) {
                        for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) {
                            values.add(rows.getMetaData().getColumnName(i).toLowerCase() + "=" + rows.getString(i));
                        }
                    }
                }
                result.put(table, values);
            }
        }
        return result;
    }
}
