package dev.portfolio.finance.service;

import dev.portfolio.finance.dto.account.ChangePasswordRequest;
import dev.portfolio.finance.dto.account.UpdatePreferencesRequest;
import dev.portfolio.finance.dto.account.UpdateProfileRequest;
import dev.portfolio.finance.dto.auth.UserResponse;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.exception.account.AccountValidationException;
import dev.portfolio.finance.exception.auth.InvalidCredentialsException;
import dev.portfolio.finance.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AccountService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse updateProfile(String authenticatedEmail, UpdateProfileRequest request) {
        User user = resolveUser(authenticatedEmail);
        user.updateProfile(request.firstName(), request.lastName(), request.displayName());
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse updatePreferences(String authenticatedEmail, UpdatePreferencesRequest request) {
        User user = resolveUser(authenticatedEmail);
        user.updatePreferences(request.dateFormat(), request.transactionPageSize());
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public void changePassword(String authenticatedEmail, ChangePasswordRequest request) {
        User user = resolveUser(authenticatedEmail);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new AccountValidationException("currentPassword", "Current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new AccountValidationException("newPassword", "New password must differ from the current password");
        }
        user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    private User resolveUser(String authenticatedEmail) {
        return userRepository.findByEmail(authenticatedEmail)
                .orElseThrow(() -> new InvalidCredentialsException("Authentication is required to access this resource"));
    }
}
