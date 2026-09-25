package dev.portfolio.finance.dto.account;

import dev.portfolio.finance.entity.DateFormatPreference;
import dev.portfolio.finance.validation.ValidTransactionPageSize;
import jakarta.validation.constraints.NotNull;

public record UpdatePreferencesRequest(
        @NotNull(message = "Date format is required")
        DateFormatPreference dateFormat,
        @NotNull(message = "Transaction page size is required")
        @ValidTransactionPageSize
        Integer transactionPageSize
) {
}
