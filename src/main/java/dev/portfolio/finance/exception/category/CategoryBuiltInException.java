package dev.portfolio.finance.exception.category;

/** Built-in categories cannot be changed or deleted through the public API. */
public class CategoryBuiltInException extends RuntimeException {

    public CategoryBuiltInException() {
        super("Built-in categories cannot be changed or deleted.");
    }
}
