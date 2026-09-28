package dev.portfolio.finance.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import dev.portfolio.finance.exception.auth.InvalidRefreshTokenException;

@ExtendWith(OutputCaptureExtension.class)
class RefreshTokenGeneratorTest {

    private static final String ZERO_TOKEN = "A".repeat(43);
    private static final String ZERO_TOKEN_SHA256 =
            "66687aadf862bd776c8fc18b8e9f8e20089714856ee233b3902a591d0d5f2925";

    private final RefreshTokenGenerator generator = new RefreshTokenGenerator();

    /** Deterministic source used only for known-answer checks. */
    private static final class FixedRandom extends SecureRandom {
        private final byte value;

        FixedRandom(int value) {
            this.value = (byte) value;
        }

        @Override
        public void nextBytes(byte[] bytes) {
            Arrays.fill(bytes, value);
        }
    }

    @Test
    void generatesUnpaddedBase64UrlEncodingOf32Bytes() {
        IssuedRefreshToken issued = generator.generate();
        String token = issued.rawToken();

        assertThat(token).hasSize(RefreshTokenGenerator.ENCODED_LENGTH).matches("[A-Za-z0-9_-]{43}");
        assertThat(token).doesNotContain("=", "+", "/");
        assertThat(Base64.getUrlDecoder().decode(token)).hasSize(32);
        assertThat(issued.tokenHash()).hasSize(32);
    }

    @Test
    void knownAnswerMatchesSha256OfDecodedBytes() {
        IssuedRefreshToken issued = new RefreshTokenGenerator(new FixedRandom(0)).generate();

        assertThat(issued.rawToken()).isEqualTo(ZERO_TOKEN);
        assertThat(HexFormat.of().formatHex(issued.tokenHash())).isEqualTo(ZERO_TOKEN_SHA256);
        assertThat(HexFormat.of().formatHex(generator.hashPresentedToken(ZERO_TOKEN)))
                .isEqualTo(ZERO_TOKEN_SHA256);
    }

    @Test
    void generatedTokensAndHashesDiffer() {
        Set<String> tokens = new HashSet<>();
        Set<String> hashes = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            IssuedRefreshToken issued = generator.generate();
            tokens.add(issued.rawToken());
            hashes.add(HexFormat.of().formatHex(issued.tokenHash()));
        }
        assertThat(tokens).hasSize(200);
        assertThat(hashes).hasSize(200);
    }

    @Test
    void presentedTokenHashIsStableAndMatchesIssuance() {
        IssuedRefreshToken issued = generator.generate();
        IssuedRefreshToken other = generator.generate();

        byte[] first = generator.hashPresentedToken(issued.rawToken());
        byte[] second = generator.hashPresentedToken(issued.rawToken());

        assertThat(first).hasSize(32).isEqualTo(second).isEqualTo(issued.tokenHash()).isNotSameAs(second);
        assertThat(generator.hashPresentedToken(other.rawToken())).isNotEqualTo(first);
    }

    @Test
    void issuedWrapperCopiesHashAndRedactsString() {
        IssuedRefreshToken issued = generator.generate();
        byte[] hash = issued.tokenHash();
        hash[0] ^= 1;

        assertThat(issued.tokenHash()).isNotEqualTo(hash);
        assertThat(issued.toString())
                .isEqualTo("IssuedRefreshToken[value redacted]")
                .doesNotContain(issued.rawToken(), HexFormat.of().formatHex(issued.tokenHash()));
        assertThat(issued).isNotInstanceOf(java.io.Serializable.class);
        assertThat(IssuedRefreshToken.class.isRecord()).isFalse();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "",
            "   ",
            // whitespace around, inside, or replacing a character
            " AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA ",
            "AAAAAAAAAAAAAAAAAAAAA AAAAAAAAAAAAAAAAAAAAA",
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\n",
            "\tAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            // padded Base64url of 32 bytes
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            // standard Base64 alphabet
            "+AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            "/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            "AAAAAAAAAAAAAAAAAAAAA+AAAAAAAAAAAAAAAAAAAAA",
            // unexpected characters
            "AAAAAAAAAAAAAAAAAAAAA.AAAAAAAAAAAAAAAAAAAAA",
            "AAAAAAAAAAAAAAAAAAAAA%AAAAAAAAAAAAAAAAAAAAA",
            "AAAAAAAAAAAAAAAAAAAAAéAAAAAAAAAAAAAAAAAAAAA",
            "AAAAAAAAAAAAAAAAAAAAA\u0000AAAAAAAAAAAAAAAAAAAAA",
            // too short / too long with a valid alphabet
            "AAAA",
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            // noncanonical: nonzero spare bits decode to the same 32 zero bytes
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAB",
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAD",
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA_"
    })
    void rejectsMalformedTokensWithoutEchoingThem(String presented) {
        assertThatThrownBy(() -> generator.hashPresentedToken(presented))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessage("Refresh token is invalid")
                .hasNoCause()
                .satisfies(ex -> {
                    if (presented != null && !presented.isBlank()) {
                        assertThat(ex.getMessage()).doesNotContain(presented.strip());
                        assertThat(ex.toString()).doesNotContain(presented.strip());
                    }
                });
    }

    @Test
    void validAlphabetWithWrongDecodedLengthIsRejected() {
        String thirtyThreeBytes = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[33]);
        String thirtyOneBytes = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[31]);

        assertThatThrownBy(() -> generator.hashPresentedToken(thirtyThreeBytes))
                .isInstanceOf(InvalidRefreshTokenException.class);
        assertThatThrownBy(() -> generator.hashPresentedToken(thirtyOneBytes))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void acceptsEveryCanonicalFinalCharacter() {
        for (char last : "AEIMQUYcgkosw048".toCharArray()) {
            assertThat(generator.hashPresentedToken("A".repeat(42) + last)).hasSize(32);
        }
        assertThat(generator.hashPresentedToken("-_09azAZ" + "A".repeat(35))).hasSize(32);
    }

    @Test
    void tokenOperationsWriteNothingToOutput(CapturedOutput output) {
        IssuedRefreshToken issued = generator.generate();
        generator.hashPresentedToken(issued.rawToken());
        assertThatThrownBy(() -> generator.hashPresentedToken(issued.rawToken() + "="))
                .isInstanceOf(InvalidRefreshTokenException.class);

        assertThat(output.getAll()).doesNotContain(issued.rawToken());
    }
}
