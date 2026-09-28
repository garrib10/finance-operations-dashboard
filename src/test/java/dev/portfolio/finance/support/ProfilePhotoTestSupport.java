package dev.portfolio.finance.support;

import dev.portfolio.finance.config.ProfilePhotoProperties;
import dev.portfolio.finance.dto.auth.UserResponseMapper;
import dev.portfolio.finance.storage.CloudinaryProfilePhotoUrlResolver;

public final class ProfilePhotoTestSupport {
    public static final String KEY = "fintrack/test/profile-photos/12345678-1234-4123-8123-123456789abc";
    public static final String URL = "https://res.cloudinary.com/test-cloud/image/upload/v1/" + KEY + ".jpg";

    private ProfilePhotoTestSupport() {}

    public static ProfilePhotoProperties properties(boolean enabled) {
        return new ProfilePhotoProperties(enabled, "test-cloud", "test-api-key", "test-api-secret",
                "fintrack/test/profile-photos", 2097152, 3145728, 4096, 4096, 12000000, 512, 512, 0.85);
    }

    public static UserResponseMapper mapper() {
        return new UserResponseMapper(new CloudinaryProfilePhotoUrlResolver(properties(true)));
    }
}
