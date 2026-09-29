package dev.portfolio.finance.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import dev.portfolio.finance.dto.auth.LoginRequest;
import dev.portfolio.finance.dto.auth.RegisterRequest;
import dev.portfolio.finance.dto.auth.UserResponse;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.exception.GlobalExceptionHandler;
import dev.portfolio.finance.exception.auth.InvalidCredentialsException;
import dev.portfolio.finance.repository.UserRepository;
import dev.portfolio.finance.security.JwtService;
import dev.portfolio.finance.service.AuthService;
import dev.portfolio.finance.service.UserService;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private static final String TEST_EMAIL =
            "test@example.com";

    @Mock
    private UserService userService;

    @Mock
    private AuthService authService;

    @Mock
    private JwtService jwtService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private dev.portfolio.finance.service.RefreshSessionService refreshSessionService;

    @Mock
    private dev.portfolio.finance.security.RefreshCookieService refreshCookieService;

    private MockMvc mockMvc;

    private TestingAuthenticationToken authentication;

    @BeforeEach
    void setUp() {
        AuthController controller =
                new AuthController(
                        userService,
                        authService,
                        jwtService,
                        userRepository,
                        dev.portfolio.finance.support.ProfilePhotoTestSupport.mapper(),
                        refreshSessionService,
                        refreshCookieService
                );

        mockMvc =
                MockMvcBuilders
                        .standaloneSetup(controller)
                        .setControllerAdvice(
                                new GlobalExceptionHandler()
                        )
                        .build();

        authentication =
                new TestingAuthenticationToken(
                        TEST_EMAIL,
                        null
                );
    }

    @Test
    void shouldRegisterUserWhenRequestIsValid()
            throws Exception {

        // Arrange
        String requestBody = """
                {
                  "firstName": "Test",
                  "lastName": "User",
                  "email": "test@example.com",
                  "password": "River meadow lantern 42!"
                }
                """;

        UserResponse response =
                new UserResponse(
                        1L,
                        "Test",
                        "User",
                        "Test User",
                        TEST_EMAIL,
                        LocalDateTime.of(
                                2026,
                                9,
                                8,
                                12,
                                0
                        ),
                        new dev.portfolio.finance.dto.account.AccountPreferencesResponse(
                                dev.portfolio.finance.entity.DateFormatPreference.MEDIUM, 10
                        ), null
                );

        when(userService.register(
                any(RegisterRequest.class)
        )).thenReturn(response);

        // Act + Assert
        mockMvc.perform(
                        post("/api/auth/register")
                                .contentType("application/json")
                                .content(requestBody)
                )
                .andExpect(
                        status().isCreated()
                )
                .andExpect(
                        jsonPath("$.email")
                                .value(TEST_EMAIL)
                );

        ArgumentCaptor<RegisterRequest> requestCaptor =
                ArgumentCaptor.forClass(
                        RegisterRequest.class
                );

        verify(userService)
                .register(
                        requestCaptor.capture()
                );

        RegisterRequest capturedRequest =
                requestCaptor.getValue();

        assertEquals(
                "Test",
                capturedRequest.firstName()
        );

        assertEquals(
                "User",
                capturedRequest.lastName()
        );

        assertEquals(
                TEST_EMAIL,
                capturedRequest.email()
        );
    }

    @Test
    void shouldReturnBadRequestWhenRegistrationRequestIsInvalid()
            throws Exception {

        // Arrange
        String requestBody = """
                {
                  "firstName": "",
                  "lastName": "User",
                  "email": "not-an-email",
                  "password": ""
                }
                """;

        // Act + Assert
        mockMvc.perform(
                        post("/api/auth/register")
                                .contentType("application/json")
                                .content(requestBody)
                )
                .andExpect(
                        status().isBadRequest()
                );

        verify(
                userService,
                never()
        ).register(
                any(RegisterRequest.class)
        );
    }

    @Test
    void shouldLoginUserWhenCredentialsAreValid()
            throws Exception {

        // Arrange
        String requestBody = """
                {
                  "email": "test@example.com",
                  "password": "Password123!"
                }
                """;

        java.time.Instant expiresAt = java.time.Instant.parse("2026-10-27T12:00:00Z");
        when(authService.login(
                any(LoginRequest.class)
        )).thenReturn(new dev.portfolio.finance.service.RefreshSessionService.IssuedSession(
                "test-jwt-token", "raw-refresh-value", expiresAt));

        when(refreshCookieService.issue("raw-refresh-value", expiresAt))
                .thenReturn("fintrack_refresh=raw-refresh-value; Path=/api/auth; HttpOnly; SameSite=Lax");

        when(jwtService.getExpirationSeconds())
                .thenReturn(300L);

        // Act + Assert
        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType("application/json")
                                .content(requestBody)
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.accessToken")
                                .value("test-jwt-token")
                )
                .andExpect(
                        jsonPath("$.tokenType")
                                .value("Bearer")
                )
                .andExpect(
                        jsonPath("$.expiresIn")
                                .value(300)
                )
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                                .string("Set-Cookie", "fintrack_refresh=raw-refresh-value; Path=/api/auth; HttpOnly; SameSite=Lax")
                )
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                                .string(org.hamcrest.Matchers.not(
                                        org.hamcrest.Matchers.containsString("raw-refresh-value")))
                );

        verify(authService)
                .login(
                        any(LoginRequest.class)
                );
    }

    @Test
    void shouldReturnUnauthorizedWhenCredentialsAreInvalid()
            throws Exception {

        // Arrange
        String requestBody = """
                {
                  "email": "test@example.com",
                  "password": "wrong-password"
                }
                """;

        when(authService.login(
                any(LoginRequest.class)
        )).thenThrow(
                new InvalidCredentialsException(
                        "Invalid email or password"
                )
        );

        // Act + Assert
        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType("application/json")
                                .content(requestBody)
                )
                .andExpect(
                        status().isUnauthorized()
                )
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                                .doesNotExist("Set-Cookie")
                );

        verify(
                refreshSessionService,
                never()
        ).startSession(
                any(), any()
        );

        verify(
                jwtService,
                never()
        ).generateToken(
                any(User.class)
        );
    }

    @Test
    void shouldReturnCurrentAuthenticatedUser()
            throws Exception {

        // Arrange
        User user =
                new User(
                        "Test",
                        "User",
                        TEST_EMAIL,
                        "hashed-password"
                );

        when(userRepository.findByEmail(
                TEST_EMAIL
        )).thenReturn(
                Optional.of(user)
        );

        // Act + Assert
        mockMvc.perform(
                        get("/api/auth/me")
                                .principal(authentication)
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.firstName")
                                .value("Test")
                )
                .andExpect(
                        jsonPath("$.lastName")
                                .value("User")
                )
                .andExpect(
                        jsonPath("$.email")
                                .value(TEST_EMAIL)
                );

        verify(userRepository)
                .findByEmail(
                        TEST_EMAIL
                );
    }
}