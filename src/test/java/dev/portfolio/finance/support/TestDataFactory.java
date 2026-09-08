package dev.portfolio.finance.support;

import java.math.BigDecimal;
import java.time.LocalDate;
import dev.portfolio.finance.entity.Budget;
import dev.portfolio.finance.entity.Category;
import dev.portfolio.finance.entity.Transaction;
import dev.portfolio.finance.entity.TransactionType;
import dev.portfolio.finance.entity.User;

public final class TestDataFactory {

    private static final String DEFAULT_FIRST_NAME = "Test";
    private static final String DEFAULT_LAST_NAME = "User";
    private static final String DEFAULT_EMAIL = "test@example.com";
    private static final String DEFAULT_PASSWORD_HASH = "test-password-hash";
    private static final String DEFAULT_CATEGORY_NAME = "Groceries";

    private static final BigDecimal DEFAULT_EXPENSE_AMOUNT =
            new BigDecimal("100.00");

    private static final BigDecimal DEFAULT_INCOME_AMOUNT =
            new BigDecimal("500.00");

    private static final BigDecimal DEFAULT_BUDGET_LIMIT =
            new BigDecimal("700.00");

    private static final LocalDate DEFAULT_TRANSACTION_DATE =
            LocalDate.of(2026, 9, 1);

    private static final int DEFAULT_BUDGET_MONTH = 9;
    private static final int DEFAULT_BUDGET_YEAR = 2026;

    private TestDataFactory() {
        // Utility class - prevent instantiation.
    }

    // =========================================================
    // User
    // =========================================================

    public static User createUser() {
        return new User(
                DEFAULT_FIRST_NAME,
                DEFAULT_LAST_NAME,
                DEFAULT_EMAIL,
                DEFAULT_PASSWORD_HASH
        );
    }

    public static User createUser(
            String firstName,
            String lastName,
            String email
    ) {
        return new User(
                firstName,
                lastName,
                email,
                DEFAULT_PASSWORD_HASH
        );
    }

    public static User createUser(
            String firstName,
            String lastName,
            String email,
            String passwordHash
    ) {
        return new User(
                firstName,
                lastName,
                email,
                passwordHash
        );
    }

    // =========================================================
    // Category
    // =========================================================

    public static Category createCategory(
            User user
    ) {
        return new Category(
                user,
                DEFAULT_CATEGORY_NAME,
                true
        );
    }

    public static Category createCategory(
            User user,
            String name
    ) {
        return new Category(
                user,
                name,
                true
        );
    }

    public static Category createCategory(
            User user,
            String name,
            boolean budgetEnabled
    ) {
        return new Category(
                user,
                name,
                budgetEnabled
        );
    }

    // =========================================================
    // Transaction
    // =========================================================

    public static Transaction createExpenseTransaction(
            User user,
            Category category
    ) {
        return new Transaction(
                user,
                category,
                TransactionType.EXPENSE,
                DEFAULT_EXPENSE_AMOUNT,
                "Test expense",
                DEFAULT_TRANSACTION_DATE
        );
    }

    public static Transaction createIncomeTransaction(
            User user,
            Category category
    ) {
        return new Transaction(
                user,
                category,
                TransactionType.INCOME,
                DEFAULT_INCOME_AMOUNT,
                "Test income",
                DEFAULT_TRANSACTION_DATE
        );
    }

    public static Transaction createTransaction(
            User user,
            Category category,
            TransactionType type,
            BigDecimal amount,
            String description,
            LocalDate transactionDate
    ) {
        return new Transaction(
                user,
                category,
                type,
                amount,
                description,
                transactionDate
        );
    }

    // =========================================================
    // Budget
    // =========================================================

    public static Budget createBudget(
            User user,
            Category category
    ) {
        return new Budget(
                user,
                category,
                DEFAULT_BUDGET_LIMIT,
                DEFAULT_BUDGET_MONTH,
                DEFAULT_BUDGET_YEAR
        );
    }

    public static Budget createBudget(
            User user,
            Category category,
            BigDecimal monthlyLimit,
            int month,
            int year
    ) {
        return new Budget(
                user,
                category,
                monthlyLimit,
                month,
                year
        );
    }
}