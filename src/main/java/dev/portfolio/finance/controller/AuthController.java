package dev.portfolio.finance.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import dev.portfolio.finance.dto.auth.LoginRequest;
import dev.portfolio.finance.dto.auth.LoginResponse;
import dev.portfolio.finance.dto.auth.RegisterRequest;
import dev.portfolio.finance.dto.auth.UserResponse;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.repository.UserRepository;
import dev.portfolio.finance.security.JwtService;
import dev.portfolio.finance.service.AuthService;
import dev.portfolio.finance.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final AuthService authService;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    public AuthController(
            UserService userService,
            AuthService authService,
            JwtService jwtService,
            UserRepository userRepository
    ) {
        this.userService = userService;
        this.authService = authService;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
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

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        User user = authService.authenticate(request);

        String token = jwtService.generateToken(user);

        LoginResponse response = new LoginResponse(
                token,
                "Bearer",
                jwtService.getExpirationMs() / 1000
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(
            Authentication authentication
    ) {
        String email = authentication.getName();

        User user = userRepository
                .findByEmail(email)
                .orElseThrow();

        UserResponse response = new UserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getCreatedAt()
        );

        return ResponseEntity.ok(response);
    }
}