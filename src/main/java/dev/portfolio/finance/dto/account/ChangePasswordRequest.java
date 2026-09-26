package dev.portfolio.finance.dto.account;

import dev.portfolio.finance.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
        @NotBlank(message = "Current password is required")
        String currentPassword,
        @ValidPassword
        String newPassword
) {
    @Override
    public String toString() {
        return "ChangePasswordRequest[currentPassword=[REDACTED], newPassword=[REDACTED]]";
    }
}
