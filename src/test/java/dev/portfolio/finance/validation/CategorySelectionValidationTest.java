package dev.portfolio.finance.validation;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.json.JsonMapper;
import dev.portfolio.finance.dto.budget.CreateBudgetRequest;
import dev.portfolio.finance.dto.budget.UpdateBudgetRequest;
import dev.portfolio.finance.dto.category.CategorySelection;
import dev.portfolio.finance.dto.category.NewCategoryRequest;
import dev.portfolio.finance.dto.transaction.CreateTransactionRequest;
import dev.portfolio.finance.dto.transaction.UpdateTransactionRequest;
import dev.portfolio.finance.entity.TransactionType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

/** The exact-one category selection rule on all four financial write requests. */
class CategorySelectionValidationTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    /** Builds each request type with a given selection and otherwise valid fields. */
    static List<BiFunction<Long, NewCategoryRequest, CategorySelection>> requestTypes() {
        BigDecimal amount = new BigDecimal("12.50");
        LocalDate date = LocalDate.of(2026, 9, 30);
        return List.of(
                (id, created) -> new CreateTransactionRequest(id, TransactionType.EXPENSE, amount, "Food", date, created),
                (id, created) -> new UpdateTransactionRequest(id, TransactionType.EXPENSE, amount, "Food", date, created),
                (id, created) -> new CreateBudgetRequest(id, amount, 9, 2026, created),
                (id, created) -> new UpdateBudgetRequest(id, amount, 9, 2026, created));
    }

    @ParameterizedTest
    @MethodSource("requestTypes")
    void acceptsExactlyOneSelection(BiFunction<Long, NewCategoryRequest, CategorySelection> request) {
        assertThat(errors(request.apply(42L, null))).isEmpty();
        assertThat(errors(request.apply(null, new NewCategoryRequest("Pet Care", null)))).isEmpty();
        assertThat(errors(request.apply(null, new NewCategoryRequest("Pet Care", "paw-print")))).isEmpty();
        assertThat(errors(request.apply(null, new NewCategoryRequest("Pet Care", "  ")))).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("requestTypes")
    void rejectsBothOrNeitherOnAStableField(BiFunction<Long, NewCategoryRequest, CategorySelection> request) {
        assertThat(errors(request.apply(42L, new NewCategoryRequest("Pet Care", null))))
                .containsExactly(Map.entry("newCategory", "Choose an existing category or a new category, not both"));
        assertThat(errors(request.apply(null, null)))
                .containsExactly(Map.entry("categoryId", "Choose an existing category or create a new one"));
    }

    @ParameterizedTest
    @MethodSource("requestTypes")
    void validatesNestedNewCategoryFields(BiFunction<Long, NewCategoryRequest, CategorySelection> request) {
        assertThat(errors(request.apply(null, new NewCategoryRequest(null, null))))
                .containsExactly(Map.entry("newCategory.name", "Category name is required"));
        assertThat(errors(request.apply(null, new NewCategoryRequest("   ", null))))
                .containsExactly(Map.entry("newCategory.name", "Category name is required"));
        assertThat(errors(request.apply(null, new NewCategoryRequest("x".repeat(101), null))))
                .containsExactly(Map.entry("newCategory.name", "Category name must be 100 characters or fewer"));
        for (String icon : List.of("Paw-Print", "<svg/>", "https://example.com/i.svg", "../tag", "dog")) {
            assertThat(errors(request.apply(null, new NewCategoryRequest("Pets", icon))))
                    .containsExactly(Map.entry("newCategory.iconKey", "Icon must be one of the approved category icons"));
        }
    }

    @Test
    void existingCategoryIdJsonStillDeserializesAndUnknownNestedFieldsAreIgnored() throws Exception {
        JsonMapper json = JsonMapper.builder()
                .configure(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .build();

        CreateTransactionRequest legacy = json.readValue("""
                {"categoryId": 7, "type": "EXPENSE", "amount": 4.50, "description": "Coffee",
                 "transactionDate": "2026-09-30"}
                """, CreateTransactionRequest.class);
        assertThat(legacy.categoryId()).isEqualTo(7L);
        assertThat(legacy.newCategory()).isNull();
        assertThat(errors(legacy)).isEmpty();

        CreateBudgetRequest created = json.readValue("""
                {"monthlyLimit": 50, "month": 9, "year": 2026,
                 "newCategory": {"name": "Pets", "iconKey": "paw-print", "builtIn": true, "userId": 99,
                                 "normalizedName": "x", "budgetEnabled": false, "id": 5}}
                """, CreateBudgetRequest.class);
        assertThat(created.newCategory()).isEqualTo(new NewCategoryRequest("Pets", "paw-print"));
        assertThat(errors(created)).isEmpty();
    }

    private static Map<String, String> errors(Object request) {
        Map<String, String> result = new TreeMap<>();
        VALIDATOR.validate(request).forEach(v -> result.put(v.getPropertyPath().toString(), v.getMessage()));
        return result;
    }
}
