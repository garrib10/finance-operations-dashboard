package dev.portfolio.finance.exception.category;

import java.util.Map;

/** Field-level category input errors, returned in the standard validation response. */
public class CategoryValidationException extends RuntimeException {

    private final Map<String, String> fields;

    public CategoryValidationException(String field, String message) {
        super("Category validation failed");
        this.fields = Map.of(field, message);
    }

    public Map<String, String> getFields() {
        return fields;
    }
}
