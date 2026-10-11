package dev.portfolio.finance.dto.category;

import dev.portfolio.finance.validation.ApprovedCategoryIcon;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A custom category created as part of a transaction or budget write. Only the name and
 * an optional approved icon are accepted; ownership, built-in status, and IDs come from
 * the server, and the category is always budget-enabled. Other JSON properties are ignored.
 */
public record NewCategoryRequest(

        @NotBlank(message = "Category name is required")
        @Size(
                max = 100,
                message = "Category name must be 100 characters or fewer"
        )
        String name,

        @ApprovedCategoryIcon
        String iconKey
) {
}
