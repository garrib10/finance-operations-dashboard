package dev.portfolio.finance.config;

import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Secrets are validated indirectly so binding failures never report their values. */
@Validated
@ConfigurationProperties("app.profile-photo")
public record ProfilePhotoProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("") String cloudName,
        @DefaultValue("") String apiKey,
        @DefaultValue("") String apiSecret,
        @DefaultValue("") String keyPrefix,
        @DefaultValue("2097152") long maxInputBytes,
        @DefaultValue("3145728") long maxRequestBytes,
        @DefaultValue("4096") int maxWidth,
        @DefaultValue("4096") int maxHeight,
        @DefaultValue("12000000") long maxDecodedPixels,
        @DefaultValue("512") int outputMaxWidth,
        @DefaultValue("512") int outputMaxHeight,
        @DefaultValue("0.85") double jpegQuality
) {
    @AssertTrue(message = "Enabled profile photos require valid backend provider configuration")
    public boolean isProviderConfigurationValid() {
        return !enabled || (cloudName.matches("[a-z0-9-]{1,63}")
                && !apiKey.isBlank() && !apiSecret.isBlank()
                && !keyPrefix.isBlank() && isKeyPrefixValid());
    }

    @AssertTrue(message = "Profile photo namespace must use bounded lowercase path segments")
    public boolean isKeyPrefixValid() {
        return keyPrefix.isEmpty() || (keyPrefix.length() <= 180
                && keyPrefix.matches("[a-z0-9-]+(?:/[a-z0-9-]+)*"));
    }

    @AssertTrue(message = "Profile photo limits must be positive and within the approved policy")
    public boolean isPolicyValid() {
        return maxInputBytes > 0 && maxInputBytes <= 2097152
                && maxRequestBytes > maxInputBytes && maxRequestBytes <= 3145728
                && maxWidth > 0 && maxWidth <= 4096 && maxHeight > 0 && maxHeight <= 4096
                && maxDecodedPixels > 0 && maxDecodedPixels <= 12000000
                && outputMaxWidth > 0 && outputMaxWidth <= 512 && outputMaxWidth <= maxWidth
                && outputMaxHeight > 0 && outputMaxHeight <= 512 && outputMaxHeight <= maxHeight
                && jpegQuality > 0 && jpegQuality <= 0.85;
    }

    @Override
    public String toString() {
        return "ProfilePhotoProperties[configuration redacted]";
    }
}
