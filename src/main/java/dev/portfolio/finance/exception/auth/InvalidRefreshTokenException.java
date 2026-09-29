package dev.portfolio.finance.exception.auth;

/** Deliberately carries no submitted value, cause, or reason detail. */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("Refresh token is invalid");
    }
}
