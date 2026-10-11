package dev.portfolio.finance.dto.category;

import java.time.LocalDateTime;

/**
 * Canonical category representation. {@code builtIn} and {@code iconKey} are
 * backend-controlled; the normalized comparison name and the owner are never included.
 */
public record CategoryResponse(
        Long id,
        String name,
        boolean budgetEnabled,
        boolean builtIn,
        String iconKey,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
