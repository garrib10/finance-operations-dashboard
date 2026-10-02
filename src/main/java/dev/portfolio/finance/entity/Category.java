package dev.portfolio.finance.entity;

import java.util.Objects;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import dev.portfolio.finance.exception.category.CategoryBuiltInException;
import dev.portfolio.finance.validation.CategoryNameNormalizer;
import dev.portfolio.finance.validation.NormalizedCategoryName;

@Entity
@Table(
        name = "categories",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_categories_user_normalized_name",
                        columnNames = {"user_id", "normalized_name"}
                ),
                // Target of the composite ownership foreign keys from transactions and budgets.
                @UniqueConstraint(
                        name = "uk_categories_id_user",
                        columnNames = {"id", "user_id"}
                )
        }
)
public class Category extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String name;

    /** Internal comparison value for per-user uniqueness; never part of API responses. */
    @Column(name = "normalized_name", nullable = false, length = 300)
    private String normalizedName;

    @Column(name = "budget_enabled", nullable = false)
    private boolean budgetEnabled;

    @Column(name = "built_in", nullable = false, updatable = false)
    private boolean builtIn;

    /**
     * Stored as the raw key so a row with a key outside today's catalog still loads;
     * {@link #getIcon()} maps it to a safe value without rewriting the row.
     */
    @Column(name = "icon_key", nullable = false, length = 64)
    private String iconKey;

    protected Category() {
    }

    private Category(
            User user,
            NormalizedCategoryName name,
            boolean budgetEnabled,
            boolean builtIn,
            CategoryIcon icon
    ) {
        this.user = Objects.requireNonNull(user, "user");
        this.budgetEnabled = budgetEnabled;
        this.builtIn = builtIn;
        this.iconKey = Objects.requireNonNull(icon, "icon").key();
        applyName(name);
    }

    /** A user-created category: custom, with the generic {@link CategoryIcon#TAG} icon. */
    public static Category custom(
            User user,
            NormalizedCategoryName name,
            boolean budgetEnabled
    ) {
        return new Category(user, name, budgetEnabled, false, CategoryIcon.TAG);
    }

    /** A user-created category with a chosen approved icon. */
    public static Category custom(
            User user,
            NormalizedCategoryName name,
            boolean budgetEnabled,
            CategoryIcon icon
    ) {
        return new Category(user, name, budgetEnabled, false, icon);
    }

    /** Normalizes {@code name}; throws {@code InvalidCategoryNameException} when invalid. */
    public static Category custom(User user, String name, boolean budgetEnabled) {
        return custom(user, CategoryNameNormalizer.normalize(name), budgetEnabled);
    }

    /** A seeded default category with its approved icon and budget flag. */
    public static Category builtIn(User user, BuiltInCategory definition) {
        return new Category(
                user,
                CategoryNameNormalizer.normalize(definition.displayName()),
                definition.budgetEnabled(),
                true,
                definition.icon()
        );
    }

    public User getUser() {
        return user;
    }

    public String getName() {
        return name;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public boolean isBudgetEnabled() {
        return budgetEnabled;
    }

    public boolean isBuiltIn() {
        return builtIn;
    }

    /** The approved icon, or {@link CategoryIcon#TAG} when the stored key is not in the catalog. */
    public CategoryIcon getIcon() {
        return CategoryIcon.fromStoredKey(iconKey);
    }

    /** The stored key exactly as persisted (internal; responses use {@link #getIcon()}). */
    public String getIconKey() {
        return iconKey;
    }

    /**
     * Renames and updates the budget flag of a custom category; ownership, built-in
     * status, and the icon are kept. Built-in categories are immutable.
     */
    public void update(
            NormalizedCategoryName name,
            boolean budgetEnabled
    ) {
        requireCustom();
        applyName(name);
        this.budgetEnabled = budgetEnabled;
    }

    public void changeIcon(CategoryIcon icon) {
        requireCustom();
        this.iconKey = Objects.requireNonNull(icon, "icon").key();
    }

    private void requireCustom() {
        if (builtIn) {
            throw new CategoryBuiltInException();
        }
    }

    private void applyName(NormalizedCategoryName name) {
        Objects.requireNonNull(name, "name");
        this.name = name.displayName();
        this.normalizedName = name.comparisonName();
    }
}
