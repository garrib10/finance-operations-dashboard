package dev.portfolio.finance.security;

/**
 * Short-lived handoff from generation: the raw value goes only to the future cookie
 * writer, the hash only to persistence. Not serializable; never log or return it.
 */
public final class IssuedRefreshToken {

    private final String rawToken;
    private final byte[] tokenHash;

    IssuedRefreshToken(String rawToken, byte[] tokenHash) {
        this.rawToken = rawToken;
        this.tokenHash = tokenHash.clone();
    }

    public String rawToken() {
        return rawToken;
    }

    public byte[] tokenHash() {
        return tokenHash.clone();
    }

    @Override
    public String toString() {
        return "IssuedRefreshToken[value redacted]";
    }
}
