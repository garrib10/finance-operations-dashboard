package dev.portfolio.finance.mysql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import dev.portfolio.finance.support.MutableClock;

/**
 * Shared real-MySQL context for *IT classes. One pinned MySQL container per test JVM
 * (removed by Testcontainers' reaper when the JVM exits). Flyway V1–V5 run on startup
 * and Hibernate validates the entity mappings against MySQL, as in production. No
 * test profile, H2, or hosted database is involved; credentials are generated per run.
 */
@SpringBootTest(properties = {
        "app.jwt.secret=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIz",
        "app.jwt.expiration-ms=300000",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.jpa.show-sql=false"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(MySqlTestClockConfig.class)
public abstract class MySqlIntegrationTestBase {

    /** MySQL 8.4 LTS: officially supported by Flyway 12, pinned to an exact patch release. */
    public static final DockerImageName MYSQL_IMAGE = DockerImageName.parse("mysql:8.4.6");

    /**
     * Intentionally never closed: one container is shared by every *IT class for the
     * whole test JVM, and Testcontainers' reaper (Ryuk) removes it when the JVM exits.
     */
    @SuppressWarnings("resource")
    protected static final MySQLContainer MYSQL = new MySQLContainer(MYSQL_IMAGE)
            .withDatabaseName("fintrack")
            .withUsername("fintrack")
            .withPassword(UUID.randomUUID().toString());

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired protected MutableClock clock;
    @Autowired protected JdbcTemplate jdbc;

    @BeforeEach
    void resetClock() {
        clock.set(Instant.now().truncatedTo(ChronoUnit.SECONDS));
    }

    /** Root connection for server introspection (InnoDB lock waits) and scratch schemas. */
    protected static Connection rootConnection(String database) throws SQLException {
        String url = MYSQL.getJdbcUrl().replace("/fintrack", "/" + database);
        return DriverManager.getConnection(url, "root", MYSQL.getPassword());
    }
}
