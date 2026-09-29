package dev.portfolio.finance.storage;

import static org.assertj.core.api.Assertions.*;
import static dev.portfolio.finance.support.ProfilePhotoTestSupport.*;
import dev.portfolio.finance.config.ProfilePhotoProperties;
import dev.portfolio.finance.exception.account.ProfilePhotoStorageException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProfilePhotoKeyGeneratorTest {
    @Test
    void generatesFreshUuidKeysInTheConfiguredNamespace() {
        var generator = new ProfilePhotoKeyGenerator(properties(true));
        var first = generator.generate(); var second = generator.generate();
        assertThat(first).isNotEqualTo(second).startsWith("fintrack/test/profile-photos/");
        assertThat(generator.isValid(first)).isTrue();
        assertThat(new ProfilePhotoKeyGenerator(properties(true), () -> UUID.fromString("12345678-1234-4123-8123-123456789abc")).generate()).isEqualTo(KEY);
        assertThat(generator.isValid(KEY.replace("/test/", "/production/"))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "../bad", "https://host", "back\\slash", "double//slash", "/leading", "trailing/", "control\ncharacter"})
    void refusesUnsafePrefixEvenWhenConstructedOutsideSpring(String prefix) {
        var p = new ProfilePhotoProperties(true, "test-cloud", "key", "secret", prefix,
                2097152, 3145728, 4096, 4096, 12000000, 512, 512, 0.85);
        assertThatThrownBy(() -> new ProfilePhotoKeyGenerator(p).generate()).isInstanceOf(ProfilePhotoStorageException.class);
    }

    @Test
    void refusesGenerationWhenDisabled() {
        assertThatThrownBy(() -> new ProfilePhotoKeyGenerator(properties(false)).generate())
                .isInstanceOfSatisfying(ProfilePhotoStorageException.class,
                        ex -> assertThat(ex.getReason()).isEqualTo(ProfilePhotoStorageException.Reason.DISABLED));
    }
}
