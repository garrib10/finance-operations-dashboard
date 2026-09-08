package dev.portfolio.finance.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.support.TestDataFactory;

class JwtServiceTest {

    private static final String TEST_SECRET =
            "0123456789012345678901234567890123456789012345678901234567890123";

    private static final long TEST_EXPIRATION_MS = 3_600_000L;

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(
                TEST_SECRET,
                TEST_EXPIRATION_MS
        );
    }

    @Test
    void shouldGenerateValidTokenAndExtractEmail() {
        // Arrange
        User user = TestDataFactory.createUser();

        // Act
        String token = jwtService.generateToken(user);

        // Assert
        assertNotNull(token);
        assertTrue(jwtService.isTokenValid(token));
        assertEquals(
                user.getEmail(),
                jwtService.extractEmail(token)
        );
    }

    @Test
    void shouldReturnFalseForInvalidToken() {
        // Arrange
        String invalidToken = "not-a-valid-jwt";

        // Act
        boolean valid = jwtService.isTokenValid(invalidToken);

        // Assert
        assertFalse(valid);
    }

    @Test
    void shouldReturnConfiguredExpirationTime() {
        // Act
        long expirationMs = jwtService.getExpirationMs();

        // Assert
        assertEquals(
                TEST_EXPIRATION_MS,
                expirationMs
        );
    }
}