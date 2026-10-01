package dev.portfolio.finance.dto.dashboard;

import java.math.BigDecimal;

public record CategorySpendingResponse(
        Long categoryId,
        String categoryName,
        /** Approved icon key of the category; "tag" when the stored key is unknown. */
        String categoryIconKey,
        BigDecimal amountSpent
) {
}