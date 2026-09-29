package dev.portfolio.finance.security;

/**
 * Result of a single access-token parse. Carries no token text or claims beyond the
 * subject and internal user ID needed to authenticate.
 */
public record AccessTokenValidation(Status status, String email, Long userId) {

    public enum Status {
        VALID,
        /** Correctly signed, structurally complete, and past its expiration. */
        EXPIRED,
        INVALID
    }

    static AccessTokenValidation valid(String email, Long userId) {
        return new AccessTokenValidation(Status.VALID, email, userId);
    }

    static AccessTokenValidation expired() {
        return new AccessTokenValidation(Status.EXPIRED, null, null);
    }

    static AccessTokenValidation invalid() {
        return new AccessTokenValidation(Status.INVALID, null, null);
    }

    public boolean isValid() {
        return status == Status.VALID;
    }
}
