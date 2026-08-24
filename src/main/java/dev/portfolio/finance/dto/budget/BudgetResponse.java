package dev.portfolio.finance.dto.budget;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BudgetResponse(
        Long id,
        Long categoryId,
        String categoryName,
        BigDecimal monthlyLimit,
        int month,
        int year,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}