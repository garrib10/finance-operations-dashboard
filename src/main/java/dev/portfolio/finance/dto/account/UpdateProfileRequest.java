package dev.portfolio.finance.dto.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank(message = "First name is required")
        @Size(max = 100, message = "First name must be 100 characters or fewer")
        String firstName,
        @NotBlank(message = "Last name is required")
        @Size(max = 100, message = "Last name must be 100 characters or fewer")
        String lastName,
        @NotBlank(message = "Display name is required")
        @Size(max = 100, message = "Display name must be 100 characters or fewer")
        String displayName
) {
    public UpdateProfileRequest {
        firstName = firstName == null ? null : firstName.trim();
        lastName = lastName == null ? null : lastName.trim();
        displayName = displayName == null ? null : displayName.trim();
    }
}
