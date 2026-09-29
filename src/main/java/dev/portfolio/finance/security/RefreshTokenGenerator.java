package dev.portfolio.finance.security;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import org.springframework.stereotype.Component;
import dev.portfolio.finance.exception.auth.InvalidRefreshTokenException;

/**
 * Opaque refresh tokens: 32 SecureRandom bytes, unpadded Base64url (43 characters),
 * persisted only as SHA-256 of the decoded bytes. Presented values are parsed
 * strictly and are never logged or echoed in exceptions.
 */
@Component
public class RefreshTokenGenerator {

    public static final int TOKEN_BYTES = 32;
    public static final int ENCODED_LENGTH = 43;

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final SecureRandom random;

    public RefreshTokenGenerator() {
        this(new SecureRandom());
    }

    RefreshTokenGenerator(SecureRandom random) {
        this.random = random;
    }

    public IssuedRefreshToken generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        try {
            return new IssuedRefreshToken(ENCODER.encodeToString(bytes), sha256(bytes));
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    /** Strictly decodes a presented token and returns its 32-byte lookup hash. */
    public byte[] hashPresentedToken(String presentedToken) {
        byte[] bytes = decode(presentedToken);
        try {
            return sha256(bytes);
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    private static byte[] decode(String token) {
        if (token == null || token.length() != ENCODED_LENGTH) {
            throw new InvalidRefreshTokenException();
        }
        for (int i = 0; i < ENCODED_LENGTH; i++) {
            if (!isBase64UrlCharacter(token.charAt(i))) {
                throw new InvalidRefreshTokenException();
            }
        }
        // 43 characters carry 258 bits; the JDK ignores the 2 spare bits, so a
        // re-encoding comparison rejects alternative spellings of the same bytes.
        byte[] bytes = DECODER.decode(token);
        if (!ENCODER.encodeToString(bytes).equals(token)) {
            Arrays.fill(bytes, (byte) 0);
            throw new InvalidRefreshTokenException();
        }
        return bytes;
    }

    private static boolean isBase64UrlCharacter(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                || c == '-' || c == '_';
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
