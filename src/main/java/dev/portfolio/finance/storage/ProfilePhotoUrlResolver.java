package dev.portfolio.finance.storage;

/** Resolves backend-owned persisted keys only; null means initials should be used. */
@FunctionalInterface
public interface ProfilePhotoUrlResolver {
    String resolveDeliveryUrl(String persistedKey);
}
