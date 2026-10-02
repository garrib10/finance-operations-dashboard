package dev.portfolio.finance.dto.category;

/**
 * How a transaction or budget request chooses its category: exactly one of an existing
 * {@code categoryId} or a {@code newCategory} to create in the same database transaction.
 */
public interface CategorySelection {

    Long categoryId();

    NewCategoryRequest newCategory();
}
