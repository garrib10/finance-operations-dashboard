package dev.portfolio.finance.dto.auth;

import java.time.LocalDateTime;
import dev.portfolio.finance.dto.account.AccountPreferencesResponse;

public record UserResponse(
        Long id,
        String firstName,
        String lastName,
        String displayName,
        String email,
        LocalDateTime createdAt,
        AccountPreferencesResponse preferences,
        String profilePhotoUrl
) {
}
