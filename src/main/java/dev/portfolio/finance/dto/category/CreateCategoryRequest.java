package dev.portfolio.finance.dto.category;

import dev.portfolio.finance.validation.ApprovedCategoryIcon;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Owner, built-in status, and IDs always come from the server; unknown JSON properties
 * such as {@code builtIn} or {@code userId} are ignored. An omitted or blank
 * {@code iconKey} means the generic {@code tag} icon.
 */
public record CreateCategoryRequest(

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

    /** Pre-icon clients keep compiling and behave as before (icon {@code tag}). */
    public CreateCategoryRequest(String name, boolean budgetEnabled) {
        this(name, budgetEnabled, null);
    }
}
