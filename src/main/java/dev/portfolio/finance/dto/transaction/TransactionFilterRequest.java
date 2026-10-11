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
        Integer size,
        /** Optional; must be one of the user's categories (404 CATEGORY_NOT_FOUND otherwise). */
        Long categoryId
) {

    /** Filters without a category, as built before category filtering existed. */
    public TransactionFilterRequest(TransactionType type, String search, LocalDate startDate, LocalDate endDate,
            BigDecimal minAmount, BigDecimal maxAmount, String sortBy, String sortDirection, Integer page,
            Integer size) {
        this(type, search, startDate, endDate, minAmount, maxAmount, sortBy, sortDirection, page, size, null);
    }
}