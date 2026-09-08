package dev.portfolio.finance.dto.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import dev.portfolio.finance.entity.TransactionType;

public record TransactionFilterRequest(
        TransactionType type,
        String search,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        String sortBy,
        String sortDirection,
        Integer page,
        Integer size
) {
}