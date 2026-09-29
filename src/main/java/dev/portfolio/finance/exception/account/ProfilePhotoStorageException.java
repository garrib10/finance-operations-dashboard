package dev.portfolio.finance.exception.account;

/** Never retain the SDK exception as a cause: it may contain credentials or payloads. */
public final class ProfilePhotoStorageException extends RuntimeException {
    public enum Reason { DISABLED, INVALID_INPUT, COLLISION, UNAVAILABLE }
    private final Reason reason;

    public ProfilePhotoStorageException(Reason reason) {
        super(switch (reason) {
            case DISABLED -> "Profile photo storage is currently unavailable.";
            case INVALID_INPUT -> "The photo storage request is invalid.";
            case COLLISION -> "The photo could not be stored. Please try again.";
            case UNAVAILABLE -> "Photo storage could not complete the request. Please try again.";
        });
        this.reason = reason;
    }

    public Reason getReason() { return reason; }
}
