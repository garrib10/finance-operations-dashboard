package dev.portfolio.finance.entity;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Backend-owned catalog of approved category icon keys. The database stores only the
 * semantic key (lowercase words joined by hyphens), never markup, URLs, file paths, CSS
 * classes, or component names; the frontend decides how each key is drawn.
 */
public enum CategoryIcon {

    HOUSE("house"),
    SHOPPING_CART("shopping-cart"),
    UTENSILS("utensils"),
    CAR("car"),
    LIGHTBULB("lightbulb"),
    SHIELD("shield"),
    HEART_PULSE("heart-pulse"),
    CLAPPERBOARD("clapperboard"),
    SHOPPING_BAG("shopping-bag"),
    PLANE("plane"),
    CIRCLE_DOLLAR_SIGN("circle-dollar-sign"),
    PIGGY_BANK("piggy-bank"),

    // Additional choices for custom categories.
    PAW_PRINT("paw-print"),
    GIFT("gift"),
    DUMBBELL("dumbbell"),
    GRADUATION_CAP("graduation-cap"),
    BABY("baby"),
    WRENCH("wrench"),
    SMARTPHONE("smartphone"),
    TV("tv"),
    MUSIC("music"),
    COFFEE("coffee"),
    WINE("wine"),
    FUEL("fuel"),
    BUS("bus"),
    SHIRT("shirt"),
    SPARKLES("sparkles"),
    PILL("pill"),
    BRIEFCASE("briefcase"),
    CREDIT_CARD("credit-card"),
    RECEIPT("receipt"),
    HAND_HEART("hand-heart"),
    SOFA("sofa"),
    SPROUT("sprout"),
    GAMEPAD_2("gamepad-2"),
    TICKET("ticket"),
    PACKAGE("package"),
    WALLET("wallet"),

    /** Generic icon for custom categories and the built-in "Other" category. */
    TAG("tag");

    private static final Map<String, CategoryIcon> BY_KEY = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(CategoryIcon::key, Function.identity()));

    private final String key;

    CategoryIcon(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    /** Exact, case-sensitive lookup; anything outside the catalog is empty. */
    public static Optional<CategoryIcon> fromKey(String key) {
        return Optional.ofNullable(key).map(BY_KEY::get);
    }

    public static boolean isApproved(String key) {
        return fromKey(key).isPresent();
    }
}
