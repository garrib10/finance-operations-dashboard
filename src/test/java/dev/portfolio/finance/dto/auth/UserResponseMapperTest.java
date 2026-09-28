package dev.portfolio.finance.dto.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static dev.portfolio.finance.support.ProfilePhotoTestSupport.*;
import dev.portfolio.finance.entity.User;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class UserResponseMapperTest {
    @Test
    void mapsNullPhotoAndRetainsExplicitJsonNull() {
        var user = new User("First", "Last", "test@example.com", "secret-hash");
        var result = mapper().toResponse(user);
        var json = new JsonMapper().valueToTree(result);
        assertThat(json.has("profilePhotoUrl")).isTrue();
        assertThat(json.get("profilePhotoUrl").isNull()).isTrue();
        assertThat(result.firstName()).isEqualTo(user.getFirstName());
        assertThat(result.lastName()).isEqualTo(user.getLastName());
        assertThat(result.email()).isEqualTo(user.getEmail());
        assertThat(result.displayName()).isEqualTo(user.getDisplayName());
        assertThat(result.id()).isEqualTo(user.getId());
        assertThat(result.createdAt()).isEqualTo(user.getCreatedAt());
    }

    @Test
    void exposesDeliveryUrlWithoutKeyPropertyOrSecrets() {
        var user = new User("First", "Last", "test@example.com", "secret-hash");
        user.changeProfilePhotoKey(KEY);
        var result = mapper().toResponse(user);
        assertThat(result.profilePhotoUrl()).isEqualTo(URL);
        String json = new JsonMapper().writeValueAsString(result);
        assertThat(json).doesNotContain("profilePhotoKey", "password", "secret-hash", "test-api-key", "test-api-secret");
        // The public ID is necessarily present inside the public CDN URL, not as a writable field.
    }
}
