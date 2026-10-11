package dev.portfolio.finance.validation;

import java.text.Normalizer;
import java.util.Locale;
import dev.portfolio.finance.exception.category.InvalidCategoryNameException;
import dev.portfolio.finance.exception.category.InvalidCategoryNameException.Reason;

/**
 * The single application implementation of category-name normalization.
 *
 * <p>Display value: NFC, every run of whitespace (Java {@code isWhitespace} or
 * {@code isSpaceChar}) collapsed to one ASCII space and trimmed, no other control
 * characters, well-formed surrogate pairs only, at most 100 UTF-16 code units.
 *
 * <p>Comparison value: the display value lowercased with {@link Locale#ROOT}, then NFC
 * again. This is locale-independent lowercase equality, not full case folding:
 * composed and decomposed spellings match, accented and unaccented names stay distinct,
 * and "Straße" and "STRASSE" stay distinct.
 *
 * <p>V6 carries a frozen copy of this algorithm for the legacy backfill. Changing this
 * class never changes an applied migration; a policy change here needs a new migration
 * that re-normalizes stored rows.
 */
public final class CategoryNameNormalizer {

    public static final int MAX_DISPLAY_LENGTH = 100;

    private CategoryNameNormalizer() {
    }

    public static NormalizedCategoryName normalize(String raw) {
        if (raw == null) {
            throw new InvalidCategoryNameException(Reason.MISSING);
        }
        requireWellFormedSurrogates(raw);

        String composed = Normalizer.normalize(raw, Normalizer.Form.NFC);
        StringBuilder display = new StringBuilder(composed.length());
        boolean pendingSpace = false;
        for (int i = 0; i < composed.length(); ) {
            int codePoint = composed.codePointAt(i);
            i += Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                pendingSpace = !display.isEmpty();
                continue;
            }
            if (Character.isISOControl(codePoint)) {
                throw new InvalidCategoryNameException(Reason.CONTROL_CHARACTER);
            }
            if (pendingSpace) {
                display.append(' ');
                pendingSpace = false;
            }
            display.appendCodePoint(codePoint);
        }

        if (display.isEmpty()) {
            throw new InvalidCategoryNameException(Reason.BLANK);
        }
        if (display.length() > MAX_DISPLAY_LENGTH) {
            throw new InvalidCategoryNameException(Reason.TOO_LONG);
        }

        String displayName = display.toString();
        String comparisonName = Normalizer.normalize(
                displayName.toLowerCase(Locale.ROOT), Normalizer.Form.NFC);
        return new NormalizedCategoryName(displayName, comparisonName);
    }

    private static void requireWellFormedSurrogates(String value) {
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (Character.isHighSurrogate(current)
                    && i + 1 < value.length()
                    && Character.isLowSurrogate(value.charAt(i + 1))) {
                i++;
            } else if (Character.isSurrogate(current)) {
                throw new InvalidCategoryNameException(Reason.MALFORMED);
            }
        }
    }
}
