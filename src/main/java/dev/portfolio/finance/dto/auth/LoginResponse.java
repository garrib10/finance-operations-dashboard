package dev.portfolio.finance.dto.auth;

public record LoginResponse(

        String accessToken,
        String tokenType,
        Long expiresIn
) {
    @Override
    public String toString() {
        return "LoginResponse[accessToken=[REDACTED]]";
    }
}