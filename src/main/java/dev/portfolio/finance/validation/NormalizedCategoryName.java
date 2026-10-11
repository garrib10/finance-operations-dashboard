package dev.portfolio.finance.validation;

/**
 * A validated category name: {@code displayName} is what users see, and
 * {@code comparisonName} is the internal value that per-user uniqueness compares.
 */
public record NormalizedCategoryName(String displayName, String comparisonName) {
}
