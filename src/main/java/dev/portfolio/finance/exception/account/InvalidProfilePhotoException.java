package dev.portfolio.finance.exception.account;

public final class InvalidProfilePhotoException extends RuntimeException {
    public enum Reason { EMPTY, TOO_LARGE, UNSUPPORTED, INVALID, DIMENSIONS, ANIMATION }
    private final Reason reason;

    public InvalidProfilePhotoException(Reason reason) {
        super(switch (reason) {
            case EMPTY -> "Choose a JPEG or PNG image.";
            case TOO_LARGE -> "The photo exceeds the allowed file size.";
            case UNSUPPORTED -> "Choose a static JPEG or PNG image.";
            case INVALID -> "The image could not be processed. Choose another image.";
            case DIMENSIONS -> "The image exceeds the allowed dimensions or pixel count.";
            case ANIMATION -> "Animated and multiple-image files are not supported.";
        });
        this.reason = reason;
    }

    public Reason getReason() { return reason; }
}
