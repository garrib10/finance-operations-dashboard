package dev.portfolio.finance.dto.account;

import dev.portfolio.finance.entity.DateFormatPreference;

public record AccountPreferencesResponse(
        DateFormatPreference dateFormat,
        int transactionPageSize
) {
}
