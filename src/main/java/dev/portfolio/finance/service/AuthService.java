package dev.portfolio.finance.service;

import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import dev.portfolio.finance.dto.auth.LoginRequest;
import dev.portfolio.finance.exception.auth.InvalidCredentialsException;
import dev.portfolio.finance.repository.UserRepository;
import dev.portfolio.finance.service.RefreshSessionService.IssuedSession;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshSessionService refreshSessionService;
    /** Compared against when the email is unknown, so both paths pay one hash check. */
    private final String unknownAccountHash;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            RefreshSessionService refreshSessionService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshSessionService = refreshSessionService;
        this.unknownAccountHash = passwordEncoder.encode("unknown-account-timing-placeholder");
    }

    /**
     * Resolves the account without a lock, then verifies the password against the
     * locked current hash inside the session-issuance transaction.
     */
    public IssuedSession login(LoginRequest request) {

        String normalizedEmail = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        Optional<Long> userId = userRepository.findIdByEmail(normalizedEmail);

        if (userId.isEmpty()) {
            passwordEncoder.matches(request.password(), unknownAccountHash);
            log.info("auth.login.failed category=invalid_credentials");
            throw new InvalidCredentialsException(
                    "Invalid email or password"
            );
        }

        IssuedSession session = refreshSessionService.startSession(
                userId.get(),
                user -> passwordEncoder.matches(request.password(), user.getPasswordHash())
        );

        log.info("auth.login.succeeded userId={}", userId.get());
        return session;
    }
}
