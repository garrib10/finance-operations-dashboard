package dev.portfolio.finance.dto.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import dev.portfolio.finance.entity.TransactionType;

public record TransactionResponse(
        Long id,
        Long categoryId,
        String categoryName,
        /** Approved icon key of the category; "tag" when the stored key is unknown. */
        String categoryIconKey,
        TransactionType type,
        BigDecimal amount,
        String description,
        LocalDate transactionDate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}