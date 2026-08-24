package dev.portfolio.finance.dto.budget;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateBudgetRequest(

        @NotNull(message = "Category ID is required")
        Long categoryId,

        @NotNull(message = "Monthly limit is required")
        @DecimalMin(
                value = "0.01",
                message = "Monthly limit must be greater than 0"
        )
        BigDecimal monthlyLimit,

        @Min(
                value = 1,
                message = "Month must be between 1 and 12"
        )
        @Max(
                value = 12,
                message = "Month must be between 1 and 12"
        )
        int month,

        @Min(
                value = 2000,
                message = "Year must be 2000 or later"
        )
        int year

) {
}