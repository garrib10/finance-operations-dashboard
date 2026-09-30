package dev.portfolio.finance.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import dev.portfolio.finance.exception.category.InvalidCategoryNameException;
import dev.portfolio.finance.support.TestDataFactory;
import dev.portfolio.finance.validation.CategoryNameNormalizer;

class CategoryTest {

    private final User user = TestDataFactory.createUser();

    @Test
    void customCategoriesAreNotBuiltInAndUseTheTagIcon() {
        Category category = Category.custom(user, "  Pet   Supplies ", true);

        assertThat(category.getUser()).isSameAs(user);
        assertThat(category.getName()).isEqualTo("Pet Supplies");
        assertThat(category.getNormalizedName()).isEqualTo("pet supplies");
        assertThat(category.isBudgetEnabled()).isTrue();
        assertThat(category.isBuiltIn()).isFalse();
        assertThat(category.getIcon()).isEqualTo(CategoryIcon.TAG);
    }

    @Test
    void customCategoryNamedLikeADefaultIsStillCustom() {
        Category category = Category.custom(user, "Housing", false);

        assertThat(category.isBuiltIn()).isFalse();
        assertThat(category.getIcon()).isEqualTo(CategoryIcon.TAG);
    }

    @Test
    void builtInCategoriesCarryTheirApprovedIconAndBudgetFlag() {
        for (BuiltInCategory definition : BuiltInCategory.values()) {
            Category category = Category.builtIn(user, definition);

            assertThat(category.getName()).isEqualTo(definition.displayName());
            assertThat(category.getNormalizedName()).isEqualTo(definition.displayName().toLowerCase(java.util.Locale.ROOT));
            assertThat(category.isBuiltIn()).isTrue();
            assertThat(category.getIcon()).isEqualTo(definition.icon());
            assertThat(category.isBudgetEnabled()).isEqualTo(definition.budgetEnabled());
        }
    }

    @Test
    void updateRecomputesTheNormalizedNameAndKeepsOwnerBuiltInStatusAndIcon() {
        Category category = Category.builtIn(user, BuiltInCategory.TRAVEL);

        category.update(CategoryNameNormalizer.normalize(" Vacations "), false);

        assertThat(category.getName()).isEqualTo("Vacations");
        assertThat(category.getNormalizedName()).isEqualTo("vacations");
        assertThat(category.isBudgetEnabled()).isFalse();
        assertThat(category.getUser()).isSameAs(user);
        assertThat(category.isBuiltIn()).isTrue();
        assertThat(category.getIcon()).isEqualTo(CategoryIcon.PLANE);
    }

    @Test
    void rejectsMissingOwnerNameOrInvalidName() {
        assertThatThrownBy(() -> Category.custom(null, "Food", true)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Category.custom(user, (dev.portfolio.finance.validation.NormalizedCategoryName) null,
                true)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Category.custom(user, "   ", true)).isInstanceOf(InvalidCategoryNameException.class);
        assertThatThrownBy(() -> Category.builtIn(user, BuiltInCategory.OTHER).update(null, true))
                .isInstanceOf(NullPointerException.class);
    }
}
