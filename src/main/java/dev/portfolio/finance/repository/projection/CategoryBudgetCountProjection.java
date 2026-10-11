package dev.portfolio.finance.repository.projection;

/** How many budgets (in any month) one user has for one category. */
public interface CategoryBudgetCountProjection {

    Long getCategoryId();

    Long getBudgetCount();
}
