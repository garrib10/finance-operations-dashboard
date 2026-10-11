package dev.portfolio.finance.service;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import dev.portfolio.finance.entity.BudgetStatus;

class BudgetMetricsTest {

    @ParameterizedTest
    @CsvSource({
            "0.00, 0.00, ON_TRACK",
            "49.99, 49.99, ON_TRACK",
            "50.00, 50.00, CAUTION",
            "74.99, 74.99, CAUTION",
            "75.00, 75.00, WARNING",
            "99.99, 99.99, WARNING",
            "100.00, 100.00, OVER_BUDGET",
            "150.00, 150.00, OVER_BUDGET"
    })
    void appliesTheStatusThresholds(String spent, String percentage, BudgetStatus status) {
        BudgetMetrics metrics = BudgetMetrics.calculate(new BigDecimal("100.00"), new BigDecimal(spent));

        assertThat(metrics.percentageUsed()).isEqualByComparingTo(percentage);
        assertThat(metrics.status()).isEqualTo(status);
    }

    @Test
    void roundsThePercentageToTwoPlacesHalfUp() {
        // 1 / 3 = 0.3333 (4 places) x 100 = 33.33
        BudgetMetrics metrics = BudgetMetrics.calculate(new BigDecimal("3.00"), new BigDecimal("1.00"));

        assertThat(metrics.percentageUsed()).isEqualTo(new BigDecimal("33.33"));
        assertThat(metrics.amountRemaining()).isEqualTo(new BigDecimal("2.00"));
    }

    @Test
    void reportsANegativeRemainderWhenOverBudget() {
        BudgetMetrics metrics = BudgetMetrics.calculate(new BigDecimal("80.00"), new BigDecimal("100.00"));

        assertThat(metrics.amountRemaining()).isEqualTo(new BigDecimal("-20.00"));
        assertThat(metrics.percentageUsed()).isEqualTo(new BigDecimal("125.00"));
        assertThat(metrics.status()).isEqualTo(BudgetStatus.OVER_BUDGET);
    }
}
