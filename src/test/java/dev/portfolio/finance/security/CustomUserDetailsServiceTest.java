package dev.portfolio.finance.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    private static final String TEST_EMAIL =
            "test@example.com";

    private static final String TEST_PASSWORD_HASH =
            "hashed-password";

    @Mock
    private UserRepository userRepository;

    private CustomUserDetailsService customUserDetailsService;

    @BeforeEach
    void setUp() {
        customUserDetailsService =
                new CustomUserDetailsService(
                        userRepository
                );
    }

    @Test
    void shouldLoadUserDetailsWhenUserExists() {

        // Arrange
        User user =
                new User(
                        "Test",
                        "User",
                        TEST_EMAIL,
                        TEST_PASSWORD_HASH
                );

        when(userRepository.findByEmail(
                TEST_EMAIL
        )).thenReturn(
                Optional.of(user)
        );

        // Act
        UserDetails userDetails =
                customUserDetailsService
                        .loadUserByUsername(
                                TEST_EMAIL
                        );

        // Assert
        assertEquals(
                TEST_EMAIL,
                userDetails.getUsername()
        );

        assertEquals(
                TEST_PASSWORD_HASH,
                userDetails.getPassword()
        );

        assertTrue(
                userDetails.getAuthorities()
                        .stream()
                        .anyMatch(authority ->
                                authority
                                        .getAuthority()
                                        .equals("USER")
                        )
        );

        verify(userRepository)
                .findByEmail(
                        TEST_EMAIL
                );
    }

    @Test
    void shouldThrowUsernameNotFoundExceptionWhenUserDoesNotExist() {

        // Arrange
        when(userRepository.findByEmail(
                TEST_EMAIL
        )).thenReturn(
                Optional.empty()
        );

        // Act + Assert
        UsernameNotFoundException exception =
                assertThrows(
                        UsernameNotFoundException.class,
                        () ->
                                customUserDetailsService
                                        .loadUserByUsername(
                                                TEST_EMAIL
                                        )
                );

        assertEquals(
                "User not found",
                exception.getMessage()
        );

        verify(userRepository)
                .findByEmail(
                        TEST_EMAIL
                );
    }
}