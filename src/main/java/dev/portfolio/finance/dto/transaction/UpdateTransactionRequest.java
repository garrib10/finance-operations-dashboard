package dev.portfolio.finance.dto.transaction;

import java.math.BigDecimal;
import dev.portfolio.finance.dto.category.CategorySelection;
import dev.portfolio.finance.dto.category.NewCategoryRequest;
import dev.portfolio.finance.validation.ExactlyOneCategorySelection;
import jakarta.validation.Valid;
import java.time.LocalDate;
import dev.portfolio.finance.entity.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@ExactlyOneCategorySelection
public record UpdateTransactionRequest(

        /** An existing category owned by the user; exclusive with {@code newCategory}. */
        Long categoryId,

        @NotNull(message = "Transaction type is required")
        TransactionType type,

        @NotNull(message = "Amount is required")
        @DecimalMin(
                value = "0.01",
                message = "Amount must be greater than 0"
        )
        BigDecimal amount,

        @NotBlank(message = "Description is required")
        @Size(
                max = 255,
                message = "Description must be 255 characters or fewer"
        )
        String description,

        @NotNull(message = "Transaction date is required")
        LocalDate transactionDate,

        /** A custom category to create in the same transaction; exclusive with {@code categoryId}. */
        @Valid
        NewCategoryRequest newCategory
) implements CategorySelection {

    /** Existing-category request, as sent by clients before {@code newCategory} existed. */
    public UpdateTransactionRequest(Long categoryId, TransactionType type, BigDecimal amount, String description,
            LocalDate transactionDate) {
        this(categoryId, type, amount, description, transactionDate, null);
    }
}
