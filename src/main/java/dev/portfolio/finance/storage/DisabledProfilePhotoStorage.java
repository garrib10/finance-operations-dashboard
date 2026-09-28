package dev.portfolio.finance.storage;

import dev.portfolio.finance.exception.account.ProfilePhotoStorageException;
import static dev.portfolio.finance.exception.account.ProfilePhotoStorageException.Reason.DISABLED;

public final class DisabledProfilePhotoStorage implements ProfilePhotoStorage {
    @Override
    public void store(String key, byte[] processedJpeg) { throw new ProfilePhotoStorageException(DISABLED); }
    @Override
    public void delete(String key) { throw new ProfilePhotoStorageException(DISABLED); }
    @Override
    public String resolveDeliveryUrl(String key) { return null; }
}
