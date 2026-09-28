package dev.portfolio.finance.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AllowedOriginsTest {

    private final AllowedOrigins origins =
            AllowedOrigins.parse(" https://app.example ,http://localhost:5173, https://staging.example/ ");

    @Test
    void normalizesConfiguredOriginsOnce() {
        assertThat(origins.values()).containsExactly(
                "https://app.example", "http://localhost:5173", "https://staging.example");
        assertThat(AllowedOrigins.parse("HTTPS://App.Example:443").values())
                .containsExactly("https://app.example");
        assertThat(AllowedOrigins.parse("http://app.example:80").values())
                .containsExactly("http://app.example");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://app.example", "http://localhost:5173", "https://staging.example",
            "HTTPS://APP.EXAMPLE", "https://app.example:443"})
    void acceptsExactApprovedOrigins(String origin) {
        assertThat(origins.isAllowedOrigin(origin)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"null", "*", "https://*.app.example", "https://evil.example",
            "http://app.example", "https://app.example:8443", "http://localhost:5174",
            "https://app.example.evil.example", "https://evilapp.example", "https://app.example/",
            "https://app.example/path", "https://user@app.example", "https://app.example?x=1",
            "https://app.example#x", " https://app.example", "https://app.example ",
            "https://app.example, https://staging.example", "ftp://app.example", "app.example",
            "//app.example", "https://", "https://app example", "javascript:alert(1)"})
    void rejectsAnythingButAnExactApprovedOrigin(String origin) {
        assertThat(origins.isAllowedOrigin(origin)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://app.example/", "https://app.example/login?next=/budgets",
            "http://localhost:5173/settings#account", "https://app.example:443/x"})
    void acceptsRefererFromApprovedOrigin(String referer) {
        assertThat(origins.isAllowedReferer(referer)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"https://evil.example/https://app.example/", "https://app.example.evil.example/",
            "https://user@app.example/", "/relative/path", "not a uri", "ftp://app.example/",
            "http://app.example/", "https://app.example:8443/"})
    void rejectsRefererFromAnyOtherOrigin(String referer) {
        assertThat(origins.isAllowedReferer(referer)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"*", "https://*.app.example", "https://app.example/path", "app.example",
            "https://user@app.example", "null", "https://app.example?x", ",", " , "})
    void startupRejectsWildcardOrNonOriginEntriesWithoutEchoingThem(String configured) {
        assertThatThrownBy(() -> AllowedOrigins.parse(configured))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FRONTEND_URLS")
                .satisfies(ex -> {
                    if (!configured.isBlank() && !configured.equals(",")) {
                        assertThat(ex.getMessage()).doesNotContain(configured.trim());
                    }
                });
    }

    @Test
    void startupRequiresAtLeastOneOrigin() {
        assertThatThrownBy(() -> AllowedOrigins.parse(null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> AllowedOrigins.parse("")).isInstanceOf(IllegalStateException.class);
    }
}
