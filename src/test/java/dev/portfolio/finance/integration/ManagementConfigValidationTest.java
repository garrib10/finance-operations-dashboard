package dev.portfolio.finance.integration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import dev.portfolio.finance.FinanceOperationsDashboardApplication;

/**
 * A mistyped health-detail setting stops startup instead of silently falling back to a
 * default that might show more than intended. (Passed as an argument, which outranks
 * application.properties, the way a Railway environment variable would.)
 */
class ManagementConfigValidationTest {

    @Test
    void anInvalidHealthDetailSettingFailsStartup() {
        SpringApplicationBuilder app = new SpringApplicationBuilder(FinanceOperationsDashboardApplication.class)
                .profiles("test");

        assertThatThrownBy(() -> app.run(
                "--server.port=0",
                "--spring.datasource.url=jdbc:h2:mem:management_validation;MODE=MySQL;NON_KEYWORDS=MONTH,YEAR",
                "--management.endpoint.health.show-details=sometimes"))
                .hasStackTraceContaining("management.endpoint.health.show-details")
                .hasStackTraceContaining("sometimes");
    }
}
