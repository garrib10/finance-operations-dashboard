package dev.portfolio.finance.dto.category;

import dev.portfolio.finance.validation.ApprovedCategoryIcon;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Full update of a custom category. An omitted or blank {@code iconKey} keeps the current
 * icon, so clients written before icons existed never reset it.
 */
public record UpdateCategoryRequest(

        @NotBlank(message = "Category name is required")
        @Size(
                max = 100,
                message = "Category name must be 100 characters or fewer"
        )
        String name,

        boolean budgetEnabled,

        @ApprovedCategoryIcon
        String iconKey
) {

    public UpdateCategoryRequest(String name, boolean budgetEnabled) {
        this(name, budgetEnabled, null);
    }
}
