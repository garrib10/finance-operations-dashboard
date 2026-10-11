package dev.portfolio.finance.exception.category;

/** A category name failed normalization. Messages are fixed and never echo the input. */
public class InvalidCategoryNameException extends RuntimeException {

    public enum Reason {
        MISSING("Category name is required"),
        BLANK("Category name is required"),
        CONTROL_CHARACTER("Category name contains unsupported characters"),
        MALFORMED("Category name contains unsupported characters"),
        TOO_LONG("Category name must be 100 characters or fewer");

        private final String message;

        Reason(String message) {
            this.message = message;
        }
    }

    private final Reason reason;

    public InvalidCategoryNameException(Reason reason) {
        super(reason.message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
