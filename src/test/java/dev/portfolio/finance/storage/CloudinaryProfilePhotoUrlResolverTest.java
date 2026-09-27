package dev.portfolio.finance.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static dev.portfolio.finance.support.ProfilePhotoTestSupport.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CloudinaryProfilePhotoUrlResolverTest {
    @Test
    void resolvesOnlyApprovedHttpsDeliveryPathWithoutNetworkAccess() {
        assertThat(new CloudinaryProfilePhotoUrlResolver(properties(true)).resolveDeliveryUrl(KEY)).isEqualTo(URL);
    }

    @Test
    void disabledHidesEvenPreviouslyStoredKeys() {
        assertThat(new CloudinaryProfilePhotoUrlResolver(properties(false)).resolveDeliveryUrl(KEY)).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"https://evil.example/photo.jpg", "//evil.example/a", "../photo.jpg",
            "fintrack/production/profile-photos/12345678-1234-4123-8123-123456789abc",
            "fintrack/test/profile-photos/12345678-1234-4123-8123-123456789abc?redirect=evil",
            "fintrack/test/profile-photos/12345678-1234-4123-8123-123456789abc.jpg",
            "fintrack/test/profile-photos/%2e%2e", "fintrack/test/profile-photos/not-a-uuid",
            "fintrack/test/profile-photos/12345678-1234-1123-8123-123456789abc"})
    void invalidOrForeignKeyUsesInitials(String key) {
        assertThat(new CloudinaryProfilePhotoUrlResolver(properties(true)).resolveDeliveryUrl(key)).isNull();
    }
}
