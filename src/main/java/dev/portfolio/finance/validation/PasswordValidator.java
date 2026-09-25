package dev.portfolio.finance.validation;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Local baseline blocklist; no credentials are sent to an external service. */
public class PasswordValidator implements ConstraintValidator<ValidPassword, String> {
    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "password", "password123", "password123456789", "password1234567890",
            "passwordpassword", "passwordpasswordpassword", "password123456!",
            "123456789012345", "1234567890123456", "12345678901234567890",
            "qwertyuiopasdfgh", "qwertyuiopasdfghjkl", "qwerty1234567890",
            "abcdefghijklmnop", "letmeinletmein123", "iloveyouiloveyou",
            "correct horse battery staple", "111111111111111", "000000000000000"
    );

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        String message;
        if (value == null || value.isEmpty()) {
            message = "Password is required";
        } else if (value.codePointCount(0, value.length()) < 15) {
            message = "Password must contain at least 15 characters";
        } else if (value.getBytes(StandardCharsets.UTF_8).length > 72) {
            message = "Password must be 72 UTF-8 bytes or fewer";
        } else if (value.isBlank() || COMMON_PASSWORDS.contains(value.toLowerCase(Locale.ROOT))) {
            message = "Choose a less commonly used password";
        } else {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
        return false;
    }
}
