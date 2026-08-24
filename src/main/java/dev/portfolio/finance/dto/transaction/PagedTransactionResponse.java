package dev.portfolio.finance.dto.transaction;

import java.util.List;

public record PagedTransactionResponse(
        List<TransactionResponse> transactions,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}