package dev.portfolio.finance.dto.account;

import dev.portfolio.finance.entity.DateFormatPreference;
import dev.portfolio.finance.validation.ValidTransactionPageSize;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.annotation.JsonDeserialize;

public record UpdatePreferencesRequest(
        @NotNull(message = "Date format is required")
        @JsonDeserialize(using = AccountPreferenceDeserializers.DateFormat.class)
        DateFormatPreference dateFormat,
        @NotNull(message = "Transaction page size is required")
        @ValidTransactionPageSize
        @JsonDeserialize(using = AccountPreferenceDeserializers.PageSize.class)
        Integer transactionPageSize
) {
}
