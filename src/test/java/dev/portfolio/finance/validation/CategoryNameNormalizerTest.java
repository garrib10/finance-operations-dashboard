package dev.portfolio.finance.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import dev.portfolio.finance.exception.category.InvalidCategoryNameException;
import dev.portfolio.finance.exception.category.InvalidCategoryNameException.Reason;

class CategoryNameNormalizerTest {

    @Test
    void trimsLeadingAndTrailingWhitespace() {
        assertThat(CategoryNameNormalizer.normalize("   Groceries \t"))
                .isEqualTo(new NormalizedCategoryName("Groceries", "groceries"));
    }

    @Test
    void collapsesRepeatedInternalWhitespaceToOneAsciiSpace() {
        assertThat(CategoryNameNormalizer.normalize("Eating   \t\n  Out").displayName()).isEqualTo("Eating Out");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\t", "\n", "\r", "\u000B", "\f", "\u001C", "\u001F", " ", " "})
    void treatsJavaWhitespaceAsSeparator(String whitespace) {
        assertThat(Character.isWhitespace(whitespace.codePointAt(0))).isTrue();
        assertThat(CategoryNameNormalizer.normalize("Home" + whitespace + "Office").displayName())
                .isEqualTo("Home Office");
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", " ", " ", " ", "　"})
    void treatsJavaSpaceCharactersAsSeparator(String space) {
        assertThat(Character.isSpaceChar(space.codePointAt(0))).isTrue();
        assertThat(CategoryNameNormalizer.normalize(space + "Home" + space + space + "Office" + space).displayName())
                .isEqualTo("Home Office");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   ", "\t\n", "  "})
    void rejectsBlankValues(String blank) {
        assertRejected(blank, Reason.BLANK);
    }

    @Test
    void rejectsNull() {
        assertRejected(null, Reason.MISSING);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Bad\u0000Name", "Bad\u0007", "\u007FDelete", "Next\u0085Line", "Esc\u001B"})
    void rejectsControlCharactersThatAreNotWhitespace(String value) {
        assertRejected(value, Reason.CONTROL_CHARACTER);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Lone\uD800", "\uDC00Lone", "Swap\uDC00\uD800", "Bad\uD83DX"})
    void rejectsMalformedSurrogateSequences(String value) {
        assertRejected(value, Reason.MALFORMED);
    }

    @Test
    void acceptsWellFormedSupplementaryCharacters() {
        assertThat(CategoryNameNormalizer.normalize("Pizza 🍕").displayName()).isEqualTo("Pizza 🍕");
    }

    @Test
    void enforcesTheHundredCodeUnitBoundaryAfterSanitizing() {
        String hundred = "a".repeat(100);
        assertThat(CategoryNameNormalizer.normalize(hundred).displayName()).hasSize(100);
        assertThat(CategoryNameNormalizer.normalize("  " + hundred + "   ").displayName()).isEqualTo(hundred);
        assertThat(CategoryNameNormalizer.normalize("🍕".repeat(50)).displayName()).hasSize(100);

        assertRejected("a".repeat(101), Reason.TOO_LONG);
        assertRejected("🍕".repeat(50) + "a", Reason.TOO_LONG);
    }

    @Test
    void comparesCaseVariantsAsEqual() {
        assertThat(comparison("Groceries")).isEqualTo(comparison("GROCERIES")).isEqualTo(comparison("gRoCeRiEs"))
                .isEqualTo("groceries");
    }

    @Test
    void comparesComposedAndDecomposedSpellingsAsEqual() {
        NormalizedCategoryName decomposed = CategoryNameNormalizer.normalize("Café");

        assertThat(decomposed.displayName()).isEqualTo("Café");
        assertThat(decomposed.comparisonName()).isEqualTo(comparison("Café")).isEqualTo(comparison("CAFÉ"));
    }

    @Test
    void keepsAccentedAndUnaccentedNamesDistinct() {
        assertThat(comparison("Café")).isNotEqualTo(comparison("Cafe"));
    }

    @Test
    void isLowercaseEqualityNotFullCaseFolding() {
        assertThat(comparison("Straße")).isEqualTo("straße");
        assertThat(comparison("STRASSE")).isEqualTo("strasse");
        assertThat(comparison("Straße")).isNotEqualTo(comparison("STRASSE"));
    }

    @Test
    void ignoresTheDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(comparison("INCOME")).isEqualTo("income");
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void failureMessagesNeverEchoTheInput() {
        assertThatThrownBy(() -> CategoryNameNormalizer.normalize("secret\u0000value"))
                .hasMessage("Category name contains unsupported characters")
                .message().doesNotContain("secret");
    }

    private static String comparison(String name) {
        return CategoryNameNormalizer.normalize(name).comparisonName();
    }

    private static void assertRejected(String value, Reason reason) {
        assertThatThrownBy(() -> CategoryNameNormalizer.normalize(value))
                .isInstanceOfSatisfying(InvalidCategoryNameException.class,
                        exception -> assertThat(exception.getReason()).isEqualTo(reason));
    }
}
