package dev.portfolio.finance.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import dev.portfolio.finance.config.ProfilePhotoProperties;
import dev.portfolio.finance.exception.account.ProfilePhotoStorageException;
import static dev.portfolio.finance.exception.account.ProfilePhotoStorageException.Reason.*;
import java.io.Closeable;
import java.io.IOException;
import java.util.Map;

public final class CloudinaryProfilePhotoStorage implements ProfilePhotoStorage, Closeable {
    private final Uploader uploader;
    private final ProfilePhotoKeyGenerator keys;
    private final ProfilePhotoUrlResolver urls;
    private final Closeable transport;

    public static CloudinaryProfilePhotoStorage create(ProfilePhotoProperties properties,
            ProfilePhotoKeyGenerator keys, ProfilePhotoUrlResolver urls) {
        var transport = new CloudinaryPhotoTransport();
        var cloudinary = new Cloudinary(Map.of("cloud_name", properties.cloudName(),
                "api_key", properties.apiKey(), "api_secret", properties.apiSecret(), "secure", true));
        return new CloudinaryProfilePhotoStorage(new Uploader(cloudinary, transport), keys, urls, transport);
    }

    CloudinaryProfilePhotoStorage(Uploader uploader, ProfilePhotoKeyGenerator keys,
            ProfilePhotoUrlResolver urls, Closeable transport) {
        this.uploader = uploader;
        this.keys = keys;
        this.urls = urls;
        this.transport = transport;
    }

    @Override
    public void store(String key, byte[] processedJpeg) {
        requireKey(key);
        // Internal contract: callers MUST use processor output, never raw upload bytes.
        if (processedJpeg == null || processedJpeg.length < 4 || processedJpeg.length > 2097152
                || (processedJpeg[0] & 255) != 255 || (processedJpeg[1] & 255) != 216
                || (processedJpeg[processedJpeg.length - 2] & 255) != 255
                || (processedJpeg[processedJpeg.length - 1] & 255) != 217) {
            throw new ProfilePhotoStorageException(INVALID_INPUT);
        }
        try {
            Map<?, ?> result = uploader.upload(processedJpeg.clone(), Map.of(
                    "resource_type", "image", "type", "upload", "public_id", key,
                    "overwrite", false, "use_filename", false, "unique_filename", false,
                    "format", "jpg", "filename", "photo.jpg", "backup", false));
            if (result != null && (Boolean.TRUE.equals(result.get("existing")) || Boolean.TRUE.equals(result.get("overwritten")))) {
                throw new ProfilePhotoStorageException(COLLISION);
            }
            if (result == null || result.containsKey("error") || !key.equals(result.get("public_id"))
                    || !"image".equals(result.get("resource_type")) || !"jpg".equals(result.get("format"))) {
                throw new ProfilePhotoStorageException(UNAVAILABLE);
            }
        } catch (ProfilePhotoStorageException ex) {
            throw ex;
        } catch (Exception ex) {
            // The generated key remains with the caller for later ambiguous-outcome cleanup.
            throw new ProfilePhotoStorageException(UNAVAILABLE);
        }
    }

    @Override
    public void delete(String key) {
        requireKey(key);
        try {
            Map<?, ?> result = uploader.destroy(key, Map.of("resource_type", "image", "type", "upload", "invalidate", true));
            if (result == null || result.containsKey("error")
                    || !("ok".equals(result.get("result")) || "not found".equals(result.get("result")))) {
                throw new ProfilePhotoStorageException(UNAVAILABLE);
            }
        } catch (ProfilePhotoStorageException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ProfilePhotoStorageException(UNAVAILABLE);
        }
    }

    @Override
    public String resolveDeliveryUrl(String key) { return urls.resolveDeliveryUrl(key); }

    private void requireKey(String key) {
        if (!keys.isValid(key)) throw new ProfilePhotoStorageException(INVALID_INPUT);
    }

    @Override
    public void close() throws IOException { transport.close(); }
}
