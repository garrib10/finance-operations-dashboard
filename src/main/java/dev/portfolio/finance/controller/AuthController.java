package dev.portfolio.finance.controller;

import java.time.LocalDateTime;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import dev.portfolio.finance.dto.auth.LoginRequest;
import dev.portfolio.finance.dto.auth.LoginResponse;
import dev.portfolio.finance.dto.auth.RegisterRequest;
import dev.portfolio.finance.dto.auth.UserResponse;
import dev.portfolio.finance.dto.auth.UserResponseMapper;
import dev.portfolio.finance.dto.error.ApiErrorResponse;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.exception.auth.SessionUnavailableException;
import dev.portfolio.finance.repository.UserRepository;
import dev.portfolio.finance.security.JwtService;
import dev.portfolio.finance.security.RefreshCookieService;
import dev.portfolio.finance.service.AuthService;
import dev.portfolio.finance.service.RefreshSessionService;
import dev.portfolio.finance.service.RefreshSessionService.IssuedSession;
import dev.portfolio.finance.service.RefreshSessionService.LogoutOutcome;
import dev.portfolio.finance.service.RefreshSessionService.RefreshResult;
import dev.portfolio.finance.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public static final String SESSION_EXPIRED = "SESSION_EXPIRED";
    public static final String SESSION_UNAVAILABLE = "SESSION_UNAVAILABLE";

    private final UserService userService;
    private final AuthService authService;
    private final JwtService jwtService;
    private final UserResponseMapper responseMapper;
    private final UserRepository userRepository;
    private final RefreshSessionService refreshSessionService;
    private final RefreshCookieService refreshCookieService;

    public AuthController(
            UserService userService,
            AuthService authService,
            JwtService jwtService,
            UserRepository userRepository,
            UserResponseMapper responseMapper,
            RefreshSessionService refreshSessionService,
            RefreshCookieService refreshCookieService
    ) {
        this.userService = userService;
        this.authService = authService;
        this.jwtService = jwtService;
        this.responseMapper = responseMapper;
        this.userRepository = userRepository;
        this.refreshSessionService = refreshSessionService;
        this.refreshCookieService = refreshCookieService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        UserResponse response = userService.register(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    /** Sets the refresh cookie only after the new family has committed. */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        return tokenResponse(authService.login(request));
    }

    /** Refresh credentials come only from the refresh cookie; any body is ignored. */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest request) {
        RefreshResult result = refreshSessionService.refresh(refreshCookieService.read(request));
        return switch (result.status()) {
            case ROTATED -> tokenResponse(result.issued());
            case UNAVAILABLE -> unavailable();
            case REJECTED -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.SET_COOKIE, refreshCookieService.clear())
                    .body(error(HttpStatus.UNAUTHORIZED, "Unauthorized",
                            "Your session has expired. Please sign in again.", SESSION_EXPIRED));
        };
    }

    /** Idempotent: revokes the presented family when possible, then clears the cookie. */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        LogoutOutcome outcome = refreshSessionService.logout(refreshCookieService.read(request));
        if (outcome == LogoutOutcome.UNAVAILABLE) {
            // Revocation is unconfirmed, so keep the cookie and let the client retry.
            return unavailable();
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookieService.clear())
                .build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(
            Authentication authentication
    ) {
        String email = authentication.getName();

        User user = userRepository
                .findByEmail(email)
                .orElseThrow();

        UserResponse response = responseMapper.toResponse(user);

        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(SessionUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleSessionUnavailable() {
        return unavailable();
    }

    private ResponseEntity<LoginResponse> tokenResponse(IssuedSession session) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        refreshCookieService.issue(session.rawRefreshToken(), session.expiresAt()))
                .body(new LoginResponse(session.accessToken(), "Bearer", jwtService.getExpirationSeconds()));
    }

    private static ResponseEntity<ApiErrorResponse> unavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(error(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable",
                        "Sign-in is temporarily unavailable. Please try again.", SESSION_UNAVAILABLE));
    }

    private static ApiErrorResponse error(HttpStatus status, String error, String message, String code) {
        return new ApiErrorResponse(LocalDateTime.now(), status.value(), error, message, code);
    }
}
