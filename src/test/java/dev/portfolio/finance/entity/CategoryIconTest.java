package dev.portfolio.finance.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CategoryIconTest {

    @Test
    void keysAreUniqueLowercaseSemanticSlugs() {
        assertThat(Arrays.stream(CategoryIcon.values()).map(CategoryIcon::key))
                .doesNotHaveDuplicates()
                .allMatch(key -> key.matches("^[a-z0-9]+(-[a-z0-9]+)*$"))
                .allMatch(key -> key.length() <= 64);
    }

    @Test
    void resolvesEveryApprovedKeyExactly() {
        for (CategoryIcon icon : CategoryIcon.values()) {
            assertThat(CategoryIcon.fromKey(icon.key())).contains(icon);
            assertThat(CategoryIcon.isApproved(icon.key())).isTrue();
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Tag", "TAG", " tag", "tag ", "house-", "<svg></svg>", "https://example.com/tag.svg",
            "icons/tag.svg", "../tag", "fa fa-tag", "Tag()", "lucide-tag", "unknown"})
    void rejectsKeysOutsideTheCatalog(String key) {
        assertThat(CategoryIcon.fromKey(key)).isEmpty();
        assertThat(CategoryIcon.isApproved(key)).isFalse();
    }

    @Test
    void builtInCategoriesUseTheApprovedIconsInSeedOrder() {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("Housing", "house");
        expected.put("Groceries", "shopping-cart");
        expected.put("Dining", "utensils");
        expected.put("Transportation", "car");
        expected.put("Utilities", "lightbulb");
        expected.put("Insurance", "shield");
        expected.put("Healthcare", "heart-pulse");
        expected.put("Entertainment", "clapperboard");
        expected.put("Shopping", "shopping-bag");
        expected.put("Travel", "plane");
        expected.put("Income", "circle-dollar-sign");
        expected.put("Savings", "piggy-bank");
        expected.put("Other", "tag");

        Map<String, String> actual = new LinkedHashMap<>();
        for (BuiltInCategory category : BuiltInCategory.values()) {
            actual.put(category.displayName(), category.icon().key());
        }
        assertThat(actual).containsExactlyEntriesOf(expected);
    }

    @Test
    void builtInBudgetFlagsMatchTheExistingDefaults() {
        assertThat(Arrays.stream(BuiltInCategory.values()).filter(category -> !category.budgetEnabled()))
                .containsExactly(BuiltInCategory.INCOME, BuiltInCategory.SAVINGS);
    }

    @Test
    void converterStoresOnlyTheKeyAndRejectsUnknownStoredKeys() {
        CategoryIconConverter converter = new CategoryIconConverter();

        assertThat(converter.convertToDatabaseColumn(CategoryIcon.HEART_PULSE)).isEqualTo("heart-pulse");
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute("piggy-bank")).isEqualTo(CategoryIcon.PIGGY_BANK);
        assertThat(converter.convertToEntityAttribute(null)).isNull();
        assertThatThrownBy(() -> converter.convertToEntityAttribute("<svg>"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unsupported category icon key");
    }
}
