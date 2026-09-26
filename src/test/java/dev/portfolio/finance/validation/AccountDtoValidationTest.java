package dev.portfolio.finance.validation;

import static org.assertj.core.api.Assertions.assertThat;
import dev.portfolio.finance.dto.account.*;
import dev.portfolio.finance.dto.auth.*;
import dev.portfolio.finance.entity.DateFormatPreference;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AccountDtoValidationTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll
    static void closeFactory() {
        FACTORY.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abcdefghijklmno", " river meadow  ", "Two quiet rivers", "ééééééééééééééé", "山山山山山山山山山山山山山山山山山山山山山山山山"})
    void acceptsValidPasswordsWithoutChangingThem(String password) {
        var registration = new RegisterRequest("A", "B", "a@example.com", password);
        var change = new ChangePasswordRequest("old", password);
        assertThat(VALIDATOR.validate(registration)).isEmpty();
        assertThat(VALIDATOR.validate(change)).isEmpty();
        assertThat(registration.password()).isEqualTo(password);
        assertThat(change.newPassword()).isEqualTo(password);
    }

    @Test
    void enforcesUtf8BoundaryAndCountsUnicodeCodePoints() {
        assertThat(VALIDATOR.validate(new ChangePasswordRequest("old", "é".repeat(36)))).isEmpty();
        assertThat(VALIDATOR.validate(new ChangePasswordRequest("old", "é".repeat(37))))
                .extracting(v -> v.getMessage()).containsExactly("Password must be 72 UTF-8 bytes or fewer");
        assertThat(VALIDATOR.validate(new ChangePasswordRequest("old", "😀".repeat(14)))).hasSize(1);
        assertThat(VALIDATOR.validate(new ChangePasswordRequest("old", "😀".repeat(15)))).isEmpty();
        assertThat(VALIDATOR.validate(new ChangePasswordRequest("old", "x".repeat(72)))).isEmpty();
        assertThat(VALIDATOR.validate(new ChangePasswordRequest("old", "x".repeat(73)))).hasSize(1);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"abcdefghijklmn", "PasswordPassword", "123456789012345", "               "})
    void rejectsInvalidPasswordsWithSafeMessages(String password) {
        var registration = VALIDATOR.validate(new RegisterRequest("A", "B", "a@example.com", password));
        var change = VALIDATOR.validate(new ChangePasswordRequest("old", password));
        assertThat(registration).hasSize(1);
        assertThat(change).hasSize(1);
        assertThat(registration.iterator().next().getPropertyPath().toString()).isEqualTo("password");
        assertThat(change.iterator().next().getPropertyPath().toString()).isEqualTo("newPassword");
        if (password != null && !password.isEmpty()) {
            assertThat(change.iterator().next().getMessage()).doesNotContain(password);
        }
    }

    @Test
    void trimsProfileNamesAndValidatesBoundaries() {
        var profile = new UpdateProfileRequest(" A ", " B ", " Display ");
        assertThat(profile).isEqualTo(new UpdateProfileRequest("A", "B", "Display"));
        assertThat(VALIDATOR.validate(profile)).isEmpty();
        assertThat(VALIDATOR.validate(new UpdateProfileRequest("a".repeat(100), "b".repeat(100), "c".repeat(100)))).isEmpty();
        assertThat(VALIDATOR.validate(new UpdateProfileRequest("a".repeat(101), "b".repeat(101), "c".repeat(101)))).hasSize(3);
        assertThat(VALIDATOR.validate(new UpdateProfileRequest(null, null, null))).hasSize(3);
        assertThat(VALIDATOR.validate(new UpdateProfileRequest(" ", " ", " "))).hasSize(3);
    }

    @Test
    void validatesPreferencesAtTheirFieldPaths() {
        for (var format : DateFormatPreference.values()) {
            for (int size : new int[]{10, 25, 50}) {
                assertThat(VALIDATOR.validate(new UpdatePreferencesRequest(format, size))).isEmpty();
            }
        }
        assertThat(VALIDATOR.validate(new UpdatePreferencesRequest(null, null))).hasSize(2);
        for (int size : new int[]{0, 9, 11, 100}) {
            assertThat(VALIDATOR.validate(new UpdatePreferencesRequest(DateFormatPreference.MEDIUM, size)))
                    .extracting(v -> v.getPropertyPath().toString()).containsExactly("transactionPageSize");
        }
    }

    @Test
    void redactsPasswordsAndDoesNotApplyNewPolicyToCurrentCredentials() {
        String old = "old12345";
        String next = "River meadow lantern 42!";
        assertThat(VALIDATOR.validate(new LoginRequest("a@example.com", old))).isEmpty();
        assertThat(VALIDATOR.validate(new ChangePasswordRequest(old, next))).isEmpty();
        assertThat(VALIDATOR.validate(new ChangePasswordRequest(" ", next)))
                .extracting(v -> v.getPropertyPath().toString()).containsExactly("currentPassword");
        assertThat(new LoginRequest("a@example.com", old).toString()).doesNotContain(old);
        assertThat(new RegisterRequest("A", "B", "a@example.com", next).toString()).doesNotContain(next);
        assertThat(new ChangePasswordRequest(old, next).toString()).doesNotContain(old, next);
    }
}
