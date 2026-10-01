package dev.portfolio.finance.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import dev.portfolio.finance.exception.category.CategoryBuiltInException;
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
    void updateRecomputesTheNormalizedNameAndKeepsOwnerCustomStatusAndIcon() {
        Category category = Category.custom(user, CategoryNameNormalizer.normalize("Trips"), true,
                CategoryIcon.PLANE);

        category.update(CategoryNameNormalizer.normalize(" Vacations "), false);

        assertThat(category.getName()).isEqualTo("Vacations");
        assertThat(category.getNormalizedName()).isEqualTo("vacations");
        assertThat(category.isBudgetEnabled()).isFalse();
        assertThat(category.getUser()).isSameAs(user);
        assertThat(category.isBuiltIn()).isFalse();
        assertThat(category.getIcon()).isEqualTo(CategoryIcon.PLANE);
        assertThat(category.getIconKey()).isEqualTo("plane");
    }

    @Test
    void customIconCanChangeToAnotherApprovedIcon() {
        Category category = Category.custom(user, "Pets", true);

        category.changeIcon(CategoryIcon.HEART_PULSE);

        assertThat(category.getIcon()).isEqualTo(CategoryIcon.HEART_PULSE);
        assertThatThrownBy(() -> category.changeIcon(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void builtInCategoriesRejectEveryMutation() {
        Category category = Category.builtIn(user, BuiltInCategory.TRAVEL);

        assertThatThrownBy(() -> category.update(CategoryNameNormalizer.normalize("Vacations"), false))
                .isInstanceOf(CategoryBuiltInException.class);
        assertThatThrownBy(() -> category.changeIcon(CategoryIcon.TAG))
                .isInstanceOf(CategoryBuiltInException.class);
        assertThat(category.getName()).isEqualTo("Travel");
        assertThat(category.isBudgetEnabled()).isTrue();
        assertThat(category.getIcon()).isEqualTo(CategoryIcon.PLANE);
    }

    @Test
    void unknownStoredIconKeyFallsBackToTagWithoutChangingTheStoredValue() {
        Category category = Category.custom(user, "Legacy", true);
        ReflectionTestUtils.setField(category, "iconKey", "retired-icon");

        assertThat(category.getIcon()).isEqualTo(CategoryIcon.TAG);
        assertThat(category.getIconKey()).isEqualTo("retired-icon");
    }

    @Test
    void rejectsMissingOwnerNameOrInvalidName() {
        assertThatThrownBy(() -> Category.custom(null, "Food", true)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Category.custom(user, (dev.portfolio.finance.validation.NormalizedCategoryName) null,
                true)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Category.custom(user, "   ", true)).isInstanceOf(InvalidCategoryNameException.class);
        assertThatThrownBy(() -> Category.custom(user, "Other", true).update(null, true))
                .isInstanceOf(NullPointerException.class);
    }
}
