package dev.portfolio.finance.service;

import dev.portfolio.finance.dto.account.ChangePasswordRequest;
import dev.portfolio.finance.dto.account.UpdatePreferencesRequest;
import dev.portfolio.finance.dto.account.UpdateProfileRequest;
import dev.portfolio.finance.dto.auth.UserResponse;
import dev.portfolio.finance.dto.auth.UserResponseMapper;
import dev.portfolio.finance.entity.RefreshSessionRevocationReason;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.exception.account.AccountValidationException;
import dev.portfolio.finance.exception.auth.InvalidCredentialsException;
import dev.portfolio.finance.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private final UserResponseMapper responseMapper;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshSessionService refreshSessionService;

    public AccountService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                          UserResponseMapper responseMapper, RefreshSessionService refreshSessionService) {
        this.responseMapper = responseMapper;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshSessionService = refreshSessionService;
    }

    @Transactional
    public UserResponse updateProfile(String authenticatedEmail, UpdateProfileRequest request) {
        User user = resolveUser(authenticatedEmail);
        user.updateProfile(request.firstName(), request.lastName(), request.displayName());
        return responseMapper.toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse updatePreferences(String authenticatedEmail, UpdatePreferencesRequest request) {
        User user = resolveUser(authenticatedEmail);
        user.updatePreferences(request.dateFormat(), request.transactionPageSize());
        return responseMapper.toResponse(userRepository.save(user));
    }

    /**
     * Locks the user row (the same lock as login/refresh/logout), verifies against the
     * locked hash, stores the new hash, and revokes every refresh family, all in one
     * transaction. Validation failures throw before any write, so nothing changes.
     * Already-issued access JWTs remain valid until they expire (at most five minutes).
     * Returns the number of families revoked.
     */
    @Transactional
    public int changePassword(String authenticatedEmail, ChangePasswordRequest request) {
        Long userId = userRepository.findIdByEmail(authenticatedEmail)
                .orElseThrow(() -> new InvalidCredentialsException("Authentication is required to access this resource"));
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new InvalidCredentialsException("Authentication is required to access this resource"));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new AccountValidationException("currentPassword", "Current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new AccountValidationException("newPassword", "New password must differ from the current password");
        }
        user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        return refreshSessionService.revokeAllForUser(userId, RefreshSessionRevocationReason.PASSWORD_CHANGE);
    }

    private User resolveUser(String authenticatedEmail) {
        return userRepository.findByEmail(authenticatedEmail)
                .orElseThrow(() -> new InvalidCredentialsException("Authentication is required to access this resource"));
    }
}
