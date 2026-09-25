package dev.portfolio.finance.exception.account;

import java.util.Map;

public class AccountValidationException extends RuntimeException {
    private final Map<String, String> fields;

    public AccountValidationException(String field, String message) {
        super("Account validation failed");
        this.fields = Map.of(field, message);
    }

    public Map<String, String> getFields() {
        return fields;
    }
}
