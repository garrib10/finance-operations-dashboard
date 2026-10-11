package dev.portfolio.finance.entity;

/**
 * The default categories seeded for every new user, in seeding order. Each user still
 * receives separate rows; this enum only defines their names, icons, and budget flags.
 * V6 keeps its own frozen copy of these names and icons for the legacy backfill.
 */
public enum BuiltInCategory {

    HOUSING("Housing", CategoryIcon.HOUSE, true),
    GROCERIES("Groceries", CategoryIcon.SHOPPING_CART, true),
    DINING("Dining", CategoryIcon.UTENSILS, true),
    TRANSPORTATION("Transportation", CategoryIcon.CAR, true),
    UTILITIES("Utilities", CategoryIcon.LIGHTBULB, true),
    INSURANCE("Insurance", CategoryIcon.SHIELD, true),
    HEALTHCARE("Healthcare", CategoryIcon.HEART_PULSE, true),
    ENTERTAINMENT("Entertainment", CategoryIcon.CLAPPERBOARD, true),
    SHOPPING("Shopping", CategoryIcon.SHOPPING_BAG, true),
    TRAVEL("Travel", CategoryIcon.PLANE, true),
    INCOME("Income", CategoryIcon.CIRCLE_DOLLAR_SIGN, false),
    SAVINGS("Savings", CategoryIcon.PIGGY_BANK, false),
    OTHER("Other", CategoryIcon.TAG, true);

    private final String displayName;
    private final CategoryIcon icon;
    private final boolean budgetEnabled;

    BuiltInCategory(String displayName, CategoryIcon icon, boolean budgetEnabled) {
        this.displayName = displayName;
        this.icon = icon;
        this.budgetEnabled = budgetEnabled;
    }

    public String displayName() {
        return displayName;
    }

    public CategoryIcon icon() {
        return icon;
    }

    public boolean budgetEnabled() {
        return budgetEnabled;
    }
}
