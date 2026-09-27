package dev.portfolio.finance.storage;

import java.util.regex.Pattern;
import dev.portfolio.finance.config.ProfilePhotoProperties;
import org.springframework.stereotype.Component;

/** Pure URL construction: no SDK, filesystem access, or network requests. */
@Component
public class CloudinaryProfilePhotoUrlResolver implements ProfilePhotoUrlResolver {
    private static final String UUID_V4 = "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}";
    private final ProfilePhotoProperties properties;
    private final Pattern keyPattern;

    public CloudinaryProfilePhotoUrlResolver(ProfilePhotoProperties properties) {
        this.properties = properties;
        this.keyPattern = Pattern.compile(Pattern.quote(properties.keyPrefix()) + "/" + UUID_V4);
    }

    @Override
    public String resolveDeliveryUrl(String persistedKey) {
        if (!properties.enabled() || persistedKey == null || !keyPattern.matcher(persistedKey).matches()) {
            return null;
        }
        return "https://res.cloudinary.com/" + properties.cloudName()
                + "/image/upload/v1/" + persistedKey + ".jpg";
    }
}
