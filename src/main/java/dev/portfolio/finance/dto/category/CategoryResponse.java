package dev.portfolio.finance.dto.category;

import java.time.LocalDateTime;

public record CategoryResponse(
        Long id,
        String name,
        boolean budgetEnabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}