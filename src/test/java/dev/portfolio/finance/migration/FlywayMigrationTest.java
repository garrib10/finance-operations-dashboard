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

        assertThat(currentVersion(flyway)).isEqualTo("2");
        assertThat(rowCount(databaseUrl, "users")).isZero();
        assertThat(rowCount(databaseUrl, "categories")).isZero();
        assertThat(rowCount(databaseUrl, "transactions")).isZero();
        assertThat(rowCount(databaseUrl, "budgets")).isZero();
        assertThat(historyCount(databaseUrl, "1", "SQL")).isEqualTo(1);
        assertThat(historyCount(databaseUrl, "2", "SQL")).isEqualTo(1);
        assertThat(transactionLookupIndexCount(databaseUrl)).isEqualTo(1);
    }

    @Test
    void shouldBaselineExistingSchemaWithoutLosingData() throws SQLException {
        String databaseUrl = databaseUrl("existing");

        createExistingSchema(databaseUrl);
        insertExistingUser(databaseUrl);

        Flyway flyway = configureFlyway(databaseUrl, true);
        flyway.migrate();

        assertThat(currentVersion(flyway)).isEqualTo("2");
        assertThat(rowCount(databaseUrl, "users")).isEqualTo(1);
        assertThat(historyCount(databaseUrl, "1", "BASELINE")).isEqualTo(1);
        assertThat(historyCount(databaseUrl, "2", "SQL")).isEqualTo(1);
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
