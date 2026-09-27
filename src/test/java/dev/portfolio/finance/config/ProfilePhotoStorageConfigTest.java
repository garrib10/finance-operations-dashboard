package dev.portfolio.finance.config;

import static org.assertj.core.api.Assertions.*;
import dev.portfolio.finance.storage.*;
import dev.portfolio.finance.exception.account.ProfilePhotoStorageException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ProfilePhotoStorageConfigTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(
            ProfilePhotoConfig.class, ProfilePhotoStorageConfig.class, ProfilePhotoKeyGenerator.class, CloudinaryProfilePhotoUrlResolver.class);

    @Test
    void disabledHasNoCloudinaryClientAndNeverClaimsSuccessfulMutations() {
        try (var constructors = org.mockito.Mockito.mockConstruction(com.cloudinary.Cloudinary.class)) {
            runner.run(context -> {
                assertThat(context).hasNotFailed();
                var storage = context.getBean(ProfilePhotoStorage.class);
                assertThat(storage).isInstanceOf(DisabledProfilePhotoStorage.class);
                assertThat(storage.resolveDeliveryUrl("anything")).isNull();
                assertThatThrownBy(() -> storage.store("anything", new byte[0])).isInstanceOf(ProfilePhotoStorageException.class);
                assertThatThrownBy(() -> storage.delete("anything")).isInstanceOf(ProfilePhotoStorageException.class);
                assertThat(constructors.constructed()).isEmpty();
            });
        }
    }

    @Test
    void enabledCreatesAdapterWithoutMakingRequests() {
        runner.withPropertyValues("app.profile-photo.enabled=true", "app.profile-photo.cloud-name=test-cloud",
                "app.profile-photo.api-key=test-key", "app.profile-photo.api-secret=test-secret",
                "app.profile-photo.key-prefix=fintrack/test/profile-photos").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(ProfilePhotoStorage.class)).isInstanceOf(CloudinaryProfilePhotoStorage.class);
            assertThat(context.getBean(ProfilePhotoUrlResolver.class)).isInstanceOf(CloudinaryProfilePhotoUrlResolver.class);
        });
    }

    @Test
    void incompleteEnabledConfigurationFailsBeforeProviderUse() {
        runner.withPropertyValues("app.profile-photo.enabled=true").run(context -> assertThat(context).hasFailed());
    }
}
