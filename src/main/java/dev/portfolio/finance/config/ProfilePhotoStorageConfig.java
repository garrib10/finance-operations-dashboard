package dev.portfolio.finance.config;

import dev.portfolio.finance.storage.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ProfilePhotoStorageConfig {
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "app.profile-photo.enabled", havingValue = "true")
    CloudinaryProfilePhotoStorage cloudinaryProfilePhotoStorage(ProfilePhotoProperties properties,
            ProfilePhotoKeyGenerator keys, CloudinaryProfilePhotoUrlResolver urls) {
        return CloudinaryProfilePhotoStorage.create(properties, keys, urls);
    }

    @Bean
    @ConditionalOnProperty(name = "app.profile-photo.enabled", havingValue = "false", matchIfMissing = true)
    ProfilePhotoStorage disabledProfilePhotoStorage() { return new DisabledProfilePhotoStorage(); }
}
