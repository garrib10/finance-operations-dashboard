package dev.portfolio.finance.dto.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import dev.portfolio.finance.entity.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateTransactionRequest(

        @NotNull(message = "Category ID is required")
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
        LocalDate transactionDate

) {
}