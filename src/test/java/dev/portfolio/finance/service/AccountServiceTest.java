package dev.portfolio.finance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.util.Optional;
import dev.portfolio.finance.dto.account.*;
import dev.portfolio.finance.entity.*;
import dev.portfolio.finance.exception.account.AccountValidationException;
import dev.portfolio.finance.exception.auth.InvalidCredentialsException;
import dev.portfolio.finance.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {
    @Mock private UserRepository users;
    @Mock private RefreshSessionService refreshSessions;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private AccountService service;
    private User user;
    private static final String EMAIL = "a@example.com";
    private static final String OLD = " River meadow lantern 42! ";
    private static final String NEXT = " Another quiet forest 73! ";

    @BeforeEach
    void setUp() {
        service = new AccountService(users, encoder, dev.portfolio.finance.support.ProfilePhotoTestSupport.mapper(),
                refreshSessions);
        user = new User("First", "Last", EMAIL, encoder.encode(OLD));
    }

    private void found() {
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    }

    /** Password changes resolve the ID, then reload the user under the row lock. */
    private void locked() {
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", 5L);
        when(users.findIdByEmail(EMAIL)).thenReturn(Optional.of(5L));
        when(users.findByIdForUpdate(5L)).thenReturn(Optional.of(user));
    }

    private void saved() {
        when(users.save(any(User.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void updatesOnlyProfileFieldsOfAuthenticatedUser() {
        found(); saved();
        User other = new User("Other", "User", "b@example.com", "other-hash");
        user.changeProfilePhotoKey(dev.portfolio.finance.support.ProfilePhotoTestSupport.KEY);
        String hash = user.getPasswordHash();
        var response = service.updateProfile(EMAIL, new UpdateProfileRequest(" New ", " Name ", " Display "));
        assertThat(response.firstName()).isEqualTo("New");
        assertThat(response.lastName()).isEqualTo("Name");
        assertThat(response.displayName()).isEqualTo("Display");
        assertThat(response.email()).isEqualTo(EMAIL);
        assertThat(response.profilePhotoUrl()).isEqualTo(dev.portfolio.finance.support.ProfilePhotoTestSupport.URL);
        assertThat(user.getPasswordHash()).isEqualTo(hash);
        assertThat(response.preferences().dateFormat()).isEqualTo(DateFormatPreference.MEDIUM);
        assertThat(response.preferences().transactionPageSize()).isEqualTo(10);
        assertThat(other.getFirstName()).isEqualTo("Other");
        verify(users).save(user);
        verify(users, never()).findByEmail("b@example.com");
    }

    @Test
    void updatesOnlyPreferences() {
        found(); saved();
        user.changeProfilePhotoKey(dev.portfolio.finance.support.ProfilePhotoTestSupport.KEY);
        String hash = user.getPasswordHash();
        var response = service.updatePreferences(EMAIL, new UpdatePreferencesRequest(DateFormatPreference.ISO, 50));
        assertThat(response.preferences().dateFormat()).isEqualTo(DateFormatPreference.ISO);
        assertThat(response.preferences().transactionPageSize()).isEqualTo(50);
        assertThat(response.firstName()).isEqualTo("First");
        assertThat(response.lastName()).isEqualTo("Last");
        assertThat(response.displayName()).isEqualTo("First Last");
        assertThat(response.email()).isEqualTo(EMAIL);
        assertThat(response.profilePhotoUrl()).isEqualTo(dev.portfolio.finance.support.ProfilePhotoTestSupport.URL);
        assertThat(user.getPasswordHash()).isEqualTo(hash);
        verify(users).save(user);
    }

    @Test
    void encodesExactNewPasswordAndPreservesOtherFields() {
        locked();
        when(refreshSessions.revokeAllForUser(5L, dev.portfolio.finance.entity.RefreshSessionRevocationReason.PASSWORD_CHANGE))
                .thenReturn(2);
        assertThat(service.changePassword(EMAIL, new ChangePasswordRequest(OLD, NEXT))).isEqualTo(2);
        assertThat(encoder.matches(OLD, user.getPasswordHash())).isFalse();
        assertThat(encoder.matches(NEXT, user.getPasswordHash())).isTrue();
        assertThat(encoder.matches(NEXT.trim(), user.getPasswordHash())).isFalse();
        assertThat(user.getEmail()).isEqualTo(EMAIL);
        assertThat(user.getDisplayName()).isEqualTo("First Last");
        assertThat(user.getDateFormat()).isEqualTo(DateFormatPreference.MEDIUM);
        assertThat(user.getTransactionPageSize()).isEqualTo(10);
        verify(users).save(user);
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(users, refreshSessions);
        order.verify(users).findByIdForUpdate(5L);
        order.verify(users).save(user);
        order.verify(refreshSessions).revokeAllForUser(5L,
                dev.portfolio.finance.entity.RefreshSessionRevocationReason.PASSWORD_CHANGE);
        verify(users, never()).findByEmail(any());
    }

    @Test
    void rejectsIncorrectCurrentPasswordWithoutWriting() {
        locked();
        user.changeProfilePhotoKey(dev.portfolio.finance.support.ProfilePhotoTestSupport.KEY);
        String hash = user.getPasswordHash();
        assertThatThrownBy(() -> service.changePassword(EMAIL, new ChangePasswordRequest("wrong", NEXT)))
                .isInstanceOfSatisfying(AccountValidationException.class,
                        ex -> assertThat(ex.getFields()).containsEntry("currentPassword", "Current password is incorrect"));
        assertThat(user.getPasswordHash()).isEqualTo(hash);
        verify(users, never()).save(any());
        verify(refreshSessions, never()).revokeAllForUser(any(), any());
    }

    @Test
    void rejectsReusedPasswordWithoutWriting() {
        locked();
        user.changeProfilePhotoKey(dev.portfolio.finance.support.ProfilePhotoTestSupport.KEY);
        String hash = user.getPasswordHash();
        assertThatThrownBy(() -> service.changePassword(EMAIL, new ChangePasswordRequest(OLD, OLD)))
                .isInstanceOfSatisfying(AccountValidationException.class,
                        ex -> assertThat(ex.getFields()).containsKey("newPassword"));
        assertThat(user.getPasswordHash()).isEqualTo(hash);
        verify(users, never()).save(any());
        verify(refreshSessions, never()).revokeAllForUser(any(), any());
    }

    @Test
    void unknownPrincipalIsHandledSafely() {
        when(users.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(users.findIdByEmail(EMAIL)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateProfile(EMAIL, new UpdateProfileRequest("A", "B", "C")))
                .isInstanceOf(InvalidCredentialsException.class).hasMessage("Authentication is required to access this resource");
        assertThatThrownBy(() -> service.updatePreferences(EMAIL, new UpdatePreferencesRequest(DateFormatPreference.ISO, 25)))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> service.changePassword(EMAIL, new ChangePasswordRequest(OLD, NEXT)))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(users, never()).save(any());
        verify(refreshSessions, never()).revokeAllForUser(any(), any());
    }
}
