package dev.portfolio.finance.dto.auth;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        LocalDateTime createdAt
) {
}