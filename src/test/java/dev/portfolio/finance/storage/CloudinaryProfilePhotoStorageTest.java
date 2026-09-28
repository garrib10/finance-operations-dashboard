package dev.portfolio.finance.storage;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static dev.portfolio.finance.support.ProfilePhotoTestSupport.*;
import com.cloudinary.Uploader;
import dev.portfolio.finance.exception.account.ProfilePhotoStorageException;
import dev.portfolio.finance.service.ProfilePhotoProcessor;
import dev.portfolio.finance.support.ProfilePhotoImages;
import java.io.Closeable;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class CloudinaryProfilePhotoStorageTest {
    private final Uploader uploader = mock(Uploader.class);
    private final Closeable transport = mock(Closeable.class);
    private final CloudinaryProfilePhotoStorage storage = new CloudinaryProfilePhotoStorage(uploader,
            new ProfilePhotoKeyGenerator(properties(true)), new CloudinaryProfilePhotoUrlResolver(properties(true)), transport);

    private byte[] processed() throws Exception {
        return new ProfilePhotoProcessor(properties(true)).process(ProfilePhotoImages.image("png", 20, 20)).bytes();
    }

    @Test
    void uploadsProcessorBytesWithFixedSafeOptions() throws Exception {
        when(uploader.upload(any(), anyMap())).thenReturn(Map.of("public_id", KEY, "format", "jpg", "resource_type", "image"));
        byte[] jpeg = processed(); storage.store(KEY, jpeg);
        verify(uploader).upload(eq(jpeg), eq(Map.of("resource_type", "image", "type", "upload", "public_id", KEY,
                "overwrite", false, "use_filename", false, "unique_filename", false, "format", "jpg", "filename", "photo.jpg", "backup", false)));
        assertThat(storage.resolveDeliveryUrl(KEY)).isEqualTo(URL);
        assertThat(storage.resolveDeliveryUrl(null)).isNull();
        storage.close(); verify(transport).close();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"https://evil.example/photo", "../bad", "fintrack/production/profile-photos/12345678-1234-4123-8123-123456789abc"})
    void rejectsBadKeysWithoutCallingSdk(String key) {
        assertThatThrownBy(() -> storage.store(key, new byte[]{1})).isInstanceOf(ProfilePhotoStorageException.class);
        assertThatThrownBy(() -> storage.delete(key)).isInstanceOf(ProfilePhotoStorageException.class);
        verifyNoInteractions(uploader);
    }

    @Test
    void rejectsMissingOrNonJpegBytes() {
        for (byte[] bytes : new byte[][]{null, new byte[0], new byte[2097153], {1,2,3,4}, {(byte)255,1,2,3},
                {(byte)255,(byte)216,1,2}, {(byte)255,(byte)216,(byte)255,2}}) {
            assertThatThrownBy(() -> storage.store(KEY, bytes)).isInstanceOf(ProfilePhotoStorageException.class);
        }
        verifyNoInteractions(uploader);
    }

    @ParameterizedTest
    @ValueSource(strings = {"existing", "overwritten"})
    void collisionsAreFailuresWithoutRetry(String flag) throws Exception {
        when(uploader.upload(any(), anyMap())).thenReturn(Map.of(flag, true));
        assertThatThrownBy(() -> storage.store(KEY, processed())).isInstanceOfSatisfying(ProfilePhotoStorageException.class,
                ex -> assertThat(ex.getReason()).isEqualTo(ProfilePhotoStorageException.Reason.COLLISION));
        verify(uploader, times(1)).upload(any(), anyMap());
    }

    @Test
    void rejectsUnexpectedProviderResponses() throws Exception {
        for (Map<?, ?> response : java.util.Arrays.<Map<?, ?>>asList(null, Map.of(), Map.of("error", "private-provider-message"),
                Map.of("public_id", "other"), Map.of("public_id", KEY, "resource_type", "raw"),
                Map.of("public_id", KEY, "resource_type", "image", "format", "png"))) {
            when(uploader.upload(any(), anyMap())).thenReturn(response);
            assertThatThrownBy(() -> storage.store(KEY, processed())).isInstanceOf(ProfilePhotoStorageException.class);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ok", "not found"})
    void deletesIdempotentlyWithInvalidation(String result) throws Exception {
        when(uploader.destroy(eq(KEY), anyMap())).thenReturn(Map.of("result", result));
        storage.delete(KEY);
        verify(uploader).destroy(KEY, Map.of("resource_type", "image", "type", "upload", "invalidate", true));
    }

    @Test
    void rejectsUnknownDeleteResult() throws Exception {
        for (Map<?, ?> result : java.util.Arrays.<Map<?, ?>>asList(null, Map.of("error", "private"), Map.of("result", "failed"))) {
            when(uploader.destroy(anyString(), anyMap())).thenReturn(result);
            assertThatThrownBy(() -> storage.delete(KEY)).isInstanceOf(ProfilePhotoStorageException.class);
        }
    }

    @Test
    void exceptionsDoNotLeakProviderDetailsOrCauses(CapturedOutput output) throws Exception {
        String secret = "SDK_SECRET_PAYLOAD_PROBE";
        when(uploader.upload(any(), anyMap())).thenThrow(new IOException(secret));
        when(uploader.destroy(anyString(), anyMap())).thenThrow(new IllegalStateException(secret));
        for (var operation : java.util.List.<org.assertj.core.api.ThrowableAssert.ThrowingCallable>of(
                () -> storage.store(KEY, processed()), () -> storage.delete(KEY))) {
            assertThatThrownBy(operation).isInstanceOfSatisfying(ProfilePhotoStorageException.class, ex -> {
                assertThat(ex.getCause()).isNull(); assertThat(ex.toString()).doesNotContain(secret);
            });
        }
        assertThat(output.getAll()).doesNotContain(secret);
        verify(uploader, times(1)).upload(any(), anyMap());
    }
}
