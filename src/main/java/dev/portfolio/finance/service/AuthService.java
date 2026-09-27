package dev.portfolio.finance.service;

import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import dev.portfolio.finance.dto.auth.LoginRequest;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.exception.auth.InvalidCredentialsException;
import dev.portfolio.finance.repository.UserRepository;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User authenticate(LoginRequest request) {

        String normalizedEmail = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        User user = userRepository
                .findByEmail(normalizedEmail)
                .orElseThrow(() -> {
                    log.info("auth.login.failed category=invalid_credentials");
                    return new InvalidCredentialsException(
                            "Invalid email or password"
                    );
                });

        if (!passwordEncoder.matches(
                request.password(),
                user.getPasswordHash()
        )) {
            log.info("auth.login.failed category=invalid_credentials");
            throw new InvalidCredentialsException(
                    "Invalid email or password"
            );
        }

        log.info("auth.login.succeeded userId={}", user.getId());
        return user;
    }
}