package dev.portfolio.finance.dto.dashboard;

import java.math.BigDecimal;

public record CategorySpendingResponse(
        Long categoryId,
        String categoryName,
        BigDecimal amountSpent
) {
}