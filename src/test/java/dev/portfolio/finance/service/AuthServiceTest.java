package dev.portfolio.finance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import dev.portfolio.finance.dto.auth.LoginRequest;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.exception.auth.InvalidCredentialsException;
import dev.portfolio.finance.repository.UserRepository;
import dev.portfolio.finance.support.TestDataFactory;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String NORMALIZED_EMAIL =
            "test@example.com";

    private static final String RAW_PASSWORD =
            "Password123!";

    private static final String PASSWORD_HASH =
            "hashed-password";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    @Test
    void shouldAuthenticateUserWithValidCredentials() {
        // Arrange
        LoginRequest request =
                new LoginRequest(
                        "  TEST@EXAMPLE.COM  ",
                        RAW_PASSWORD
                );

        User user =
                TestDataFactory.createUser(
                        "Test",
                        "User",
                        NORMALIZED_EMAIL,
                        PASSWORD_HASH
                );

        when(userRepository.findByEmail(NORMALIZED_EMAIL))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                RAW_PASSWORD,
                PASSWORD_HASH
        )).thenReturn(true);

        // Act
        User authenticatedUser =
                authService.authenticate(request);

        // Assert
        assertEquals(
                user,
                authenticatedUser
        );

        verify(userRepository)
                .findByEmail(NORMALIZED_EMAIL);

        verify(passwordEncoder)
                .matches(
                        RAW_PASSWORD,
                        PASSWORD_HASH
                );
    }

    @Test
    void shouldThrowInvalidCredentialsWhenEmailDoesNotExist() {
        // Arrange
        LoginRequest request =
                new LoginRequest(
                        "  MISSING@EXAMPLE.COM  ",
                        RAW_PASSWORD
                );

        String normalizedEmail =
                "missing@example.com";

        when(userRepository.findByEmail(normalizedEmail))
                .thenReturn(Optional.empty());

        // Act + Assert
        InvalidCredentialsException exception =
                assertThrows(
                        InvalidCredentialsException.class,
                        () -> authService.authenticate(request)
                );

        assertEquals(
                "Invalid email or password",
                exception.getMessage()
        );

        verify(userRepository)
                .findByEmail(normalizedEmail);

        verify(passwordEncoder, never())
                .matches(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString()
                );
    }

    @Test
    void shouldThrowInvalidCredentialsWhenPasswordIsIncorrect() {
        // Arrange
        LoginRequest request =
                new LoginRequest(
                        NORMALIZED_EMAIL,
                        "WrongPassword123!"
                );

        User user =
                TestDataFactory.createUser(
                        "Test",
                        "User",
                        NORMALIZED_EMAIL,
                        PASSWORD_HASH
                );

        when(userRepository.findByEmail(NORMALIZED_EMAIL))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "WrongPassword123!",
                PASSWORD_HASH
        )).thenReturn(false);

        // Act + Assert
        InvalidCredentialsException exception =
                assertThrows(
                        InvalidCredentialsException.class,
                        () -> authService.authenticate(request)
                );

        assertEquals(
                "Invalid email or password",
                exception.getMessage()
        );

        verify(userRepository)
                .findByEmail(NORMALIZED_EMAIL);

        verify(passwordEncoder)
                .matches(
                        "WrongPassword123!",
                        PASSWORD_HASH
                );
    }
}