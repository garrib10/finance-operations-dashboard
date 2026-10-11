package dev.portfolio.finance.validation;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import dev.portfolio.finance.dto.budget.CreateBudgetRequest;
import dev.portfolio.finance.dto.budget.UpdateBudgetRequest;
import dev.portfolio.finance.dto.transaction.CreateTransactionRequest;
import dev.portfolio.finance.dto.transaction.UpdateTransactionRequest;
import dev.portfolio.finance.entity.TransactionType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

/** Amounts and limits must fit the DECIMAL(12,2) columns: 10 whole digits, 2 decimals. */
class FinancialAmountPrecisionTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();
    private static final LocalDate DATE = LocalDate.of(2026, 9, 30);

    static List<Arguments> requests() {
        return List.of(
                Arguments.of("amount", "Amount", (Function<BigDecimal, Object>) value ->
                        new CreateTransactionRequest(1L, TransactionType.EXPENSE, value, "x", DATE)),
                Arguments.of("amount", "Amount", (Function<BigDecimal, Object>) value ->
                        new UpdateTransactionRequest(1L, TransactionType.EXPENSE, value, "x", DATE)),
                Arguments.of("monthlyLimit", "Monthly limit", (Function<BigDecimal, Object>) value ->
                        new CreateBudgetRequest(1L, value, 9, 2026)),
                Arguments.of("monthlyLimit", "Monthly limit", (Function<BigDecimal, Object>) value ->
                        new UpdateBudgetRequest(1L, value, 9, 2026)));
    }

    @ParameterizedTest
    @MethodSource("requests")
    void acceptsValuesThatFitTheColumn(String field, String label, Function<BigDecimal, Object> request) {
        for (String value : List.of("0.01", "12.3", "12.30", "9999999999.99")) {
            assertThat(errors(request.apply(new BigDecimal(value)))).as(value).isEmpty();
        }
    }

    @ParameterizedTest
    @MethodSource("requests")
    void rejectsValuesThatWouldOverflowOrBeRounded(String field, String label, Function<BigDecimal, Object> request) {
        for (String value : List.of("10000000000.00", "99999999999999.00", "12.345", "1.005")) {
            assertThat(errors(request.apply(new BigDecimal(value)))).as(value)
                    .containsEntry(field, label + " can have at most 10 whole digits and 2 decimal places");
        }
    }

    private static Map<String, String> errors(Object request) {
        Map<String, String> result = new TreeMap<>();
        VALIDATOR.validate(request).forEach(v -> result.put(v.getPropertyPath().toString(), v.getMessage()));
        return result;
    }
}
