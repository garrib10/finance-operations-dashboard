package dev.portfolio.finance.exception.auth;

/** Temporary session-persistence failure. Carries no cause or database detail. */
public class SessionUnavailableException extends RuntimeException {

    public SessionUnavailableException() {
        super("Sign-in is temporarily unavailable");
    }
}
