package dev.portfolio.finance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import dev.portfolio.finance.dto.auth.RegisterRequest;
import dev.portfolio.finance.dto.auth.UserResponse;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.exception.auth.DuplicateEmailException;
import dev.portfolio.finance.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private CategoryInitializationService categoryInitializationService;

    @InjectMocks
    private UserService userService;

    @Test
    void shouldRegisterUserWithNormalizedEmailAndHashedPassword() {
        // Arrange
        RegisterRequest request =
                new RegisterRequest(
                        "  Test  ",
                        "  User  ",
                        "  TEST@EXAMPLE.COM  ",
                        "Password123!"
                );

        String normalizedEmail =
                "test@example.com";

        String passwordHash =
                "hashed-password";

        when(userRepository.existsByEmail(normalizedEmail))
                .thenReturn(false);

        when(passwordEncoder.encode("Password123!"))
                .thenReturn(passwordHash);

        when(userRepository.save(
                org.mockito.ArgumentMatchers.any(User.class)
        )).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        UserResponse response =
                userService.register(request);

        // Assert
        ArgumentCaptor<User> userCaptor =
                ArgumentCaptor.forClass(User.class);

        verify(userRepository)
                .save(userCaptor.capture());

        User savedUser =
                userCaptor.getValue();

        assertEquals(
                "Test",
                savedUser.getFirstName()
        );

        assertEquals(
                "User",
                savedUser.getLastName()
        );

        assertEquals(
                normalizedEmail,
                savedUser.getEmail()
        );

        assertEquals(
                passwordHash,
                savedUser.getPasswordHash()
        );

        assertEquals(
                "Test",
                response.firstName()
        );

        assertEquals(
                "User",
                response.lastName()
        );

        assertEquals(
                normalizedEmail,
                response.email()
        );

        verify(userRepository)
                .existsByEmail(normalizedEmail);

        verify(passwordEncoder)
                .encode("Password123!");

        verify(categoryInitializationService)
                .createDefaultCategories(savedUser);
    }

    @Test
    void shouldThrowDuplicateEmailExceptionWhenEmailAlreadyExists() {
        // Arrange
        RegisterRequest request =
                new RegisterRequest(
                        "Test",
                        "User",
                        "  TEST@EXAMPLE.COM  ",
                        "Password123!"
                );

        String normalizedEmail =
                "test@example.com";

        when(userRepository.existsByEmail(normalizedEmail))
                .thenReturn(true);

        // Act + Assert
        assertThrows(
                DuplicateEmailException.class,
                () -> userService.register(request)
        );

        verify(userRepository)
                .existsByEmail(normalizedEmail);

        verify(passwordEncoder, never())
                .encode(
                        org.mockito.ArgumentMatchers.anyString()
                );

        verify(userRepository, never())
                .save(
                        org.mockito.ArgumentMatchers.any(User.class)
                );

        verify(categoryInitializationService, never())
                .createDefaultCategories(
                        org.mockito.ArgumentMatchers.any(User.class)
                );
    }
}