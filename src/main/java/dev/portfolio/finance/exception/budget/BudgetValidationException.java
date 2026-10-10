package dev.portfolio.finance.exception.budget;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Field-level errors in a budget query (month and year), in the standard validation response. */
public class BudgetValidationException extends RuntimeException {

    private final Map<String, String> fields;

    /** Several field errors at once, in the given order. */
    public BudgetValidationException(Map<String, String> fields) {
        super("Budget validation failed");
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    public Map<String, String> getFields() {
        return fields;
    }
}
