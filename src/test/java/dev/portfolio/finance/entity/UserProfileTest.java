package dev.portfolio.finance.entity;

import static org.assertj.core.api.Assertions.assertThat;
import dev.portfolio.finance.dto.auth.UserResponse;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class UserProfileTest {
    @Test
    void defaultsAndMapsSafeResponse() {
        var user = new User("Test", "User", "test@example.com", "secret-hash");
        var response = UserResponse.from(user);
        assertThat(response.displayName()).isEqualTo("Test User");
        assertThat(response.preferences().dateFormat()).isEqualTo(DateFormatPreference.MEDIUM);
        assertThat(response.preferences().transactionPageSize()).isEqualTo(10);
        assertThat(response.email()).isEqualTo(user.getEmail());
        var json = new JsonMapper().writeValueAsString(response);
        assertThat(json).contains("displayName", "preferences", "MEDIUM")
                .doesNotContain("password", "passwordHash", "secret-hash");
    }

    @Test
    void boundsDefaultDisplayNameWithoutSplittingUnicodeAndHandlesBlankNames() {
        assertThat(new User(" ", " ", "a@example.com", "hash").getDisplayName()).isEqualTo("Account");
        assertThat(new User("  First  ", " Last ", "a@example.com", "hash").getDisplayName()).isEqualTo("First Last");
        assertThat(new User("a".repeat(100), "b".repeat(100), "a@example.com", "hash").getDisplayName()).isEqualTo("a".repeat(100));
        assertThat(new User("😀".repeat(100), "Last", "a@example.com", "hash").getDisplayName()).isEqualTo("😀".repeat(100));
    }
}
