package dev.portfolio.finance.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import dev.portfolio.finance.entity.User;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    private static final String TEST_EMAIL =
            "test@example.com";

    private static final Long TEST_USER_ID =
            1L;

    private static final long EXPIRATION_MS =
            3600000L;

    @Mock
    private User user;

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        String secret =
                Base64.getEncoder()
                        .encodeToString(
                                new byte[32]
                        );

        jwtService =
                new JwtService(
                        secret,
                        EXPIRATION_MS
                );
    }

    @Test
    void shouldGenerateValidToken() {

        // Arrange
        when(user.getEmail())
                .thenReturn(TEST_EMAIL);

        when(user.getId())
                .thenReturn(TEST_USER_ID);

        // Act
        String token =
                jwtService.generateToken(user);

        // Assert
        assertTrue(
                jwtService.isTokenValid(token)
        );
    }

    @Test
    void shouldExtractEmailFromToken() {

        // Arrange
        when(user.getEmail())
                .thenReturn(TEST_EMAIL);

        when(user.getId())
                .thenReturn(TEST_USER_ID);

        String token =
                jwtService.generateToken(user);

        // Act
        String email =
                jwtService.extractEmail(token);

        // Assert
        assertEquals(
                TEST_EMAIL,
                email
        );
    }

    @Test
    void shouldExtractUserIdFromToken() {

        // Arrange
        when(user.getEmail())
                .thenReturn(TEST_EMAIL);

        when(user.getId())
                .thenReturn(TEST_USER_ID);

        String token =
                jwtService.generateToken(user);

        // Act
        Long userId =
                jwtService.extractUserId(token);

        // Assert
        assertEquals(
                TEST_USER_ID,
                userId
        );
    }

    @Test
    void shouldReturnFalseForMalformedToken() {

        // Act
        boolean valid =
                jwtService.isTokenValid(
                        "not-a-valid-jwt"
                );

        // Assert
        assertFalse(valid);
    }

    @Test
    void shouldReturnFalseWhenTokenUsesDifferentSigningKey() {

        // Arrange
        when(user.getEmail())
                .thenReturn(TEST_EMAIL);

        when(user.getId())
                .thenReturn(TEST_USER_ID);

        String token =
                jwtService.generateToken(user);

        byte[] differentKeyBytes =
                new byte[32];

        differentKeyBytes[0] = 1;

        String differentSecret =
                Base64.getEncoder()
                        .encodeToString(
                                differentKeyBytes
                        );

        JwtService differentJwtService =
                new JwtService(
                        differentSecret,
                        EXPIRATION_MS
                );

        // Act
        boolean valid =
                differentJwtService
                        .isTokenValid(token);

        // Assert
        assertFalse(valid);
    }

    @Test
    void shouldReturnConfiguredExpirationTime() {

        assertEquals(
                EXPIRATION_MS,
                jwtService.getExpirationMs()
        );
    }
}