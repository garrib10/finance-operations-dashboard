package dev.portfolio.finance.dto.budget;

import java.math.BigDecimal;
import dev.portfolio.finance.dto.category.CategorySelection;
import dev.portfolio.finance.dto.category.NewCategoryRequest;
import dev.portfolio.finance.validation.ExactlyOneCategorySelection;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@ExactlyOneCategorySelection
public record UpdateBudgetRequest(

        /** An existing category owned by the user; exclusive with {@code newCategory}. */
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
        int year,

        /** A custom category to create in the same transaction; exclusive with {@code categoryId}. */
        @Valid
        NewCategoryRequest newCategory
) implements CategorySelection {

    /** Existing-category request, as sent by clients before {@code newCategory} existed. */
    public UpdateBudgetRequest(Long categoryId, BigDecimal monthlyLimit, int month, int year) {
        this(categoryId, monthlyLimit, month, year, null);
    }
}
