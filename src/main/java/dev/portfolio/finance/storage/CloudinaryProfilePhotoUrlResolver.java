package dev.portfolio.finance.storage;

import dev.portfolio.finance.config.ProfilePhotoProperties;
import org.springframework.stereotype.Component;

/** Pure URL construction: no SDK, filesystem access, or network requests. */
@Component
@org.springframework.context.annotation.Primary
public class CloudinaryProfilePhotoUrlResolver implements ProfilePhotoUrlResolver {
    private final ProfilePhotoProperties properties;
    private final ProfilePhotoKeyGenerator keys;

    public CloudinaryProfilePhotoUrlResolver(ProfilePhotoProperties properties) {
        this.properties = properties;
        this.keys = new ProfilePhotoKeyGenerator(properties);
    }

    @Override
    public String resolveDeliveryUrl(String persistedKey) {
        if (!properties.enabled() || !keys.isValid(persistedKey)) {
            return null;
        }
        return "https://res.cloudinary.com/" + properties.cloudName()
                + "/image/upload/v1/" + persistedKey + ".jpg";
    }
}
