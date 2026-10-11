package dev.portfolio.finance.exception.category;

/** The category is referenced by transactions or budgets, so it cannot be deleted. */
public class CategoryInUseException extends RuntimeException {

    public CategoryInUseException() {
        super("This category is used by transactions or budgets and cannot be deleted.");
    }
}
