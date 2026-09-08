package dev.portfolio.finance.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;

import dev.portfolio.finance.entity.TransactionType;

public record RecentTransactionResponse(
        Long id,
        Long categoryId,
        String categoryName,
        TransactionType type,
        BigDecimal amount,
        String description,
        LocalDate transactionDate
) {
}