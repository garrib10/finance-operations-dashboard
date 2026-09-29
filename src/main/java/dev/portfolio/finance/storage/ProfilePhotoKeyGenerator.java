package dev.portfolio.finance.storage;

import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import dev.portfolio.finance.config.ProfilePhotoProperties;
import dev.portfolio.finance.exception.account.ProfilePhotoStorageException;
import static dev.portfolio.finance.exception.account.ProfilePhotoStorageException.Reason.*;
import org.springframework.stereotype.Component;

@Component
public class ProfilePhotoKeyGenerator {
    private static final String UUID_V4 = "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}";
    private final ProfilePhotoProperties properties;
    private final Supplier<UUID> random;
    private final Pattern keys;

    @org.springframework.beans.factory.annotation.Autowired
    public ProfilePhotoKeyGenerator(ProfilePhotoProperties properties) {
        this(properties, UUID::randomUUID);
    }

    ProfilePhotoKeyGenerator(ProfilePhotoProperties properties, Supplier<UUID> random) {
        this.properties = properties;
        this.random = random;
        this.keys = Pattern.compile(Pattern.quote(properties.keyPrefix()) + "/" + UUID_V4);
    }

    public String generate() {
        if (!properties.enabled()) throw new ProfilePhotoStorageException(DISABLED);
        String key = properties.keyPrefix() + "/" + random.get();
        if (!isValid(key)) throw new ProfilePhotoStorageException(INVALID_INPUT);
        return key;
    }

    public boolean isValid(String key) {
        return !properties.keyPrefix().isBlank() && properties.isKeyPrefixValid()
                && key != null && keys.matcher(key).matches();
    }
}
