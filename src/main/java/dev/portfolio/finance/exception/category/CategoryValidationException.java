package dev.portfolio.finance.exception.category;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Field-level category input errors, returned in the standard validation response. */
public class CategoryValidationException extends RuntimeException {

    private final Map<String, String> fields;

    public CategoryValidationException(String field, String message) {
        super("Category validation failed");
        this.fields = Map.of(field, message);
    }

    /** Several field errors at once, in the given order. */
    public CategoryValidationException(Map<String, String> fields) {
        super("Category validation failed");
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    public Map<String, String> getFields() {
        return fields;
    }
}
