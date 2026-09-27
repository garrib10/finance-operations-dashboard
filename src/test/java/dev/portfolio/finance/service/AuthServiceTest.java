package dev.portfolio.finance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import dev.portfolio.finance.dto.auth.LoginRequest;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.exception.auth.InvalidCredentialsException;
import dev.portfolio.finance.repository.UserRepository;
import dev.portfolio.finance.service.RefreshSessionService.IssuedSession;
import dev.portfolio.finance.support.TestDataFactory;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String NORMALIZED_EMAIL = "test@example.com";
    private static final String RAW_PASSWORD = "Password123!";
    private static final String PASSWORD_HASH = "hashed-password";
    private static final String PLACEHOLDER_HASH = "placeholder-hash";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshSessionService refreshSessionService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenReturn(PLACEHOLDER_HASH);
        authService = new AuthService(userRepository, passwordEncoder, refreshSessionService);
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<Predicate<User>> credentialCheck() {
        return ArgumentCaptor.forClass(Predicate.class);
    }

    @Test
    void shouldStartSessionWhenLockedUserPasswordMatches() {
        LoginRequest request = new LoginRequest("  TEST@EXAMPLE.COM  ", RAW_PASSWORD);
        IssuedSession issued = new IssuedSession("access", "refresh", Instant.parse("2026-10-27T00:00:00Z"));
        when(userRepository.findIdByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.of(7L));
        ArgumentCaptor<Predicate<User>> check = credentialCheck();
        when(refreshSessionService.startSession(eq(7L), check.capture())).thenReturn(issued);

        assertThat(authService.login(request)).isSameAs(issued);

        // The check runs inside the locked transaction against the reloaded user.
        User locked = TestDataFactory.createUser("Test", "User", NORMALIZED_EMAIL, PASSWORD_HASH);
        when(passwordEncoder.matches(RAW_PASSWORD, PASSWORD_HASH)).thenReturn(true);
        assertThat(check.getValue().test(locked)).isTrue();
        verify(passwordEncoder).matches(RAW_PASSWORD, PASSWORD_HASH);
    }

    @Test
    void shouldThrowInvalidCredentialsWhenEmailDoesNotExist() {
        LoginRequest request = new LoginRequest("  MISSING@EXAMPLE.COM  ", RAW_PASSWORD);
        when(userRepository.findIdByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");

        // A placeholder hash is still checked so unknown emails cost one BCrypt comparison.
        verify(passwordEncoder).matches(RAW_PASSWORD, PLACEHOLDER_HASH);
        verify(refreshSessionService, never()).startSession(any(), any());
    }

    @Test
    void shouldRejectWhenLockedUserPasswordDoesNotMatch() {
        LoginRequest request = new LoginRequest(NORMALIZED_EMAIL, "wrong-password");
        when(userRepository.findIdByEmail(NORMALIZED_EMAIL)).thenReturn(Optional.of(7L));
        ArgumentCaptor<Predicate<User>> check = credentialCheck();
        when(refreshSessionService.startSession(eq(7L), check.capture()))
                .thenThrow(new InvalidCredentialsException("Invalid email or password"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");

        User locked = TestDataFactory.createUser("Test", "User", NORMALIZED_EMAIL, PASSWORD_HASH);
        when(passwordEncoder.matches("wrong-password", PASSWORD_HASH)).thenReturn(false);
        assertThat(check.getValue().test(locked)).isFalse();
    }
}
