package dev.portfolio.finance.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import dev.portfolio.finance.entity.BudgetStatus;

/**
 * The one definition of budget progress, shared by budget analytics, the dashboard, and
 * the category summary so they can never disagree.
 */
public record BudgetMetrics(
        BigDecimal amountRemaining,
        BigDecimal percentageUsed,
        BudgetStatus status
) {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal WARNING_THRESHOLD = BigDecimal.valueOf(75);
    private static final BigDecimal CAUTION_THRESHOLD = BigDecimal.valueOf(50);

    /** {@code monthlyLimit} is always positive (validated as at least 0.01). */
    public static BudgetMetrics calculate(BigDecimal monthlyLimit, BigDecimal amountSpent) {
        BigDecimal percentageUsed = amountSpent
                .divide(monthlyLimit, 4, RoundingMode.HALF_UP)
                .multiply(HUNDRED)
                .setScale(2, RoundingMode.HALF_UP);

        return new BudgetMetrics(
                monthlyLimit.subtract(amountSpent),
                percentageUsed,
                statusFor(percentageUsed)
        );
    }

    private static BudgetStatus statusFor(BigDecimal percentageUsed) {
        if (percentageUsed.compareTo(HUNDRED) >= 0) {
            return BudgetStatus.OVER_BUDGET;
        }
        if (percentageUsed.compareTo(WARNING_THRESHOLD) >= 0) {
            return BudgetStatus.WARNING;
        }
        if (percentageUsed.compareTo(CAUTION_THRESHOLD) >= 0) {
            return BudgetStatus.CAUTION;
        }
        return BudgetStatus.ON_TRACK;
    }
}
