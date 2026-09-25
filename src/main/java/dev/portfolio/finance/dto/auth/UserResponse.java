package dev.portfolio.finance.dto.auth;

import java.time.LocalDateTime;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.dto.account.AccountPreferencesResponse;

public record UserResponse(
        Long id,
        String firstName,
        String lastName,
        String displayName,
        String email,
        LocalDateTime createdAt,
        AccountPreferencesResponse preferences
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(), user.getFirstName(), user.getLastName(),
                user.getDisplayName(), user.getEmail(), user.getCreatedAt(),
                new AccountPreferencesResponse(user.getDateFormat(), user.getTransactionPageSize())
        );
    }
}