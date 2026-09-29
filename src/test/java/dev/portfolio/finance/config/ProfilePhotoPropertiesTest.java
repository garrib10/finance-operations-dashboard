package dev.portfolio.finance.config;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class ProfilePhotoPropertiesTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ProfilePhotoConfig.class);
    private static final String PREFIX = "app.profile-photo.";

    private ApplicationContextRunner enabled() {
        return runner.withPropertyValues(PREFIX + "enabled=true", PREFIX + "cloud-name=test-cloud",
                PREFIX + "api-key=key-probe-value", PREFIX + "api-secret=secret-probe-value",
                PREFIX + "key-prefix=fintrack/test/profile-photos");
    }

    @Test
    void disabledStartsWithoutCredentialsWithApprovedDefaults() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var config = context.getBean(ProfilePhotoProperties.class);
            assertThat(config.enabled()).isFalse();
            assertThat(config.maxInputBytes()).isEqualTo(2097152);
            assertThat(config.maxRequestBytes()).isEqualTo(3145728);
            assertThat(config.maxWidth()).isEqualTo(4096);
            assertThat(config.maxHeight()).isEqualTo(4096);
            assertThat(config.maxDecodedPixels()).isEqualTo(12000000);
            assertThat(config.outputMaxWidth()).isEqualTo(512);
            assertThat(config.outputMaxHeight()).isEqualTo(512);
            assertThat(config.jpegQuality()).isEqualTo(0.85);
        });
    }

    @Test
    void enabledAcceptsCompleteConfigurationAndRedactsStringRepresentation() {
        enabled().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(ProfilePhotoProperties.class).toString())
                    .doesNotContain("key-probe-value", "secret-probe-value", "test-cloud");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"cloud-name=", "api-key=", "api-secret=", "key-prefix=",
            "cloud-name=https://bad.example", "key-prefix=../production", "key-prefix=/leading",
            "key-prefix=trailing/", "key-prefix=encoded%2fpath", "key-prefix=UpperCase"})
    void rejectsIncompleteOrUnsafeProviderConfigurationWithoutLeakingSecrets(String property, CapturedOutput output) {
        enabled().withPropertyValues(PREFIX + property).run(context -> {
            assertThat(context).hasFailed();
            var text = new java.io.StringWriter();
            context.getStartupFailure().printStackTrace(new java.io.PrintWriter(text));
            assertThat(text.toString()).doesNotContain("key-probe-value", "secret-probe-value");
        });
        assertThat(output.getAll()).doesNotContain("key-probe-value", "secret-probe-value");
    }

    @ParameterizedTest
    @ValueSource(strings = {"max-input-bytes=0", "max-input-bytes=2097153",
            "max-request-bytes=0", "max-request-bytes=2097152", "max-request-bytes=3145729",
            "max-width=0", "max-width=4097", "max-height=0", "max-height=4097",
            "max-decoded-pixels=0", "max-decoded-pixels=12000001",
            "output-max-width=0", "output-max-width=513", "output-max-height=0", "output-max-height=513",
            "max-width=256", "max-height=256", "jpeg-quality=0", "jpeg-quality=0.86", "jpeg-quality=NaN"})
    void rejectsUnsafePolicyValuesEvenWhileDisabled(String property) {
        runner.withPropertyValues(PREFIX + property).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsOversizedNamespace() {
        enabled().withPropertyValues(PREFIX + "key-prefix=" + "a".repeat(181))
                .run(context -> assertThat(context).hasFailed());
    }
}
