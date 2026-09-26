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

        assertThat(currentVersion(flyway)).isEqualTo("3");
        assertThat(rowCount(databaseUrl, "users")).isZero();
        assertThat(rowCount(databaseUrl, "categories")).isZero();
        assertThat(rowCount(databaseUrl, "transactions")).isZero();
        assertThat(rowCount(databaseUrl, "budgets")).isZero();
        assertThat(historyCount(databaseUrl, "1", "SQL")).isEqualTo(1);
        assertThat(historyCount(databaseUrl, "2", "SQL")).isEqualTo(1);
        assertThat(historyCount(databaseUrl, "3", "SQL")).isEqualTo(1);
        assertThat(transactionLookupIndexCount(databaseUrl)).isEqualTo(1);
    }

    @Test
    void shouldBaselineExistingSchemaWithoutLosingData() throws SQLException {
        String databaseUrl = databaseUrl("existing");

        createExistingSchema(databaseUrl);
        insertExistingUser(databaseUrl);

        Flyway flyway = configureFlyway(databaseUrl, true);
        flyway.migrate();

        assertThat(currentVersion(flyway)).isEqualTo("3");
        assertThat(rowCount(databaseUrl, "users")).isEqualTo(1);
        assertThat(historyCount(databaseUrl, "1", "BASELINE")).isEqualTo(1);
        assertThat(historyCount(databaseUrl, "2", "SQL")).isEqualTo(1);
        assertThat(historyCount(databaseUrl, "3", "SQL")).isEqualTo(1);
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
            assertThat(currentVersion(flyway)).isEqualTo("3");
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

    private Flyway configureFlyway(
            String databaseUrl,
            boolean baselineOnMigrate
    ) {
        return Flyway.configure()
                .dataSource(databaseUrl, "sa", "")
                .locations("classpath:db/migration")
                .baselineOnMigrate(baselineOnMigrate)
                .baselineVersion(MigrationVersion.fromVersion("1"))
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
