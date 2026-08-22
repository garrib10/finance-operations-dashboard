package dev.portfolio.finance.dto.auth;

public record LoginResponse(

        String accessToken,
        String tokenType,
        Long expiresIn
) {
}