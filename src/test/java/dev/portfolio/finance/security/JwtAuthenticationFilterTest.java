package dev.portfolio.finance.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final String TEST_EMAIL =
            "test@example.com";

    private static final String TEST_TOKEN =
            "valid-jwt-token";

    @Mock
    private JwtService jwtService;

    @Mock
    private CustomUserDetailsService userDetailsService;

    @Mock
    private FilterChain filterChain;

    @Mock
    private UserDetails userDetails;

    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private MockHttpServletRequest request;

    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        jwtAuthenticationFilter =
                new JwtAuthenticationFilter(
                        jwtService,
                        userDetailsService
                );

        request =
                new MockHttpServletRequest();

        response =
                new MockHttpServletResponse();

        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldContinueFilterChainWhenAuthorizationHeaderIsMissing()
            throws ServletException, IOException {

        // Act
        jwtAuthenticationFilter.doFilter(
                request,
                response,
                filterChain
        );

        // Assert
        verify(filterChain)
                .doFilter(
                        request,
                        response
                );

        verify(jwtService, never())
                .isTokenValid(
                        org.mockito.ArgumentMatchers.anyString()
                );

        assertNull(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        );
    }

    @Test
    void shouldContinueFilterChainWhenAuthorizationHeaderIsNotBearer()
            throws ServletException, IOException {

        // Arrange
        request.addHeader(
                "Authorization",
                "Basic abc123"
        );

        // Act
        jwtAuthenticationFilter.doFilter(
                request,
                response,
                filterChain
        );

        // Assert
        verify(filterChain)
                .doFilter(
                        request,
                        response
                );

        verify(jwtService, never())
                .isTokenValid(
                        org.mockito.ArgumentMatchers.anyString()
                );

        assertNull(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        );
    }

    @Test
    void shouldContinueFilterChainWhenTokenIsInvalid()
            throws ServletException, IOException {

        // Arrange
        request.addHeader(
                "Authorization",
                "Bearer " + TEST_TOKEN
        );

        when(jwtService.isTokenValid(
                TEST_TOKEN
        )).thenReturn(false);

        // Act
        jwtAuthenticationFilter.doFilter(
                request,
                response,
                filterChain
        );

        // Assert
        verify(jwtService)
                .isTokenValid(
                        TEST_TOKEN
                );

        verify(jwtService, never())
                .extractEmail(
                        org.mockito.ArgumentMatchers.anyString()
                );

        verify(userDetailsService, never())
                .loadUserByUsername(
                        org.mockito.ArgumentMatchers.anyString()
                );

        verify(filterChain)
                .doFilter(
                        request,
                        response
                );

        assertNull(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        );
    }

    @Test
    void shouldSetAuthenticationWhenTokenIsValid()
            throws ServletException, IOException {

        // Arrange
        request.addHeader(
                "Authorization",
                "Bearer " + TEST_TOKEN
        );

        when(jwtService.isTokenValid(
                TEST_TOKEN
        )).thenReturn(true);

        when(jwtService.extractEmail(
                TEST_TOKEN
        )).thenReturn(TEST_EMAIL);

        when(userDetailsService.loadUserByUsername(
                TEST_EMAIL
        )).thenReturn(userDetails);

       when(userDetails.getAuthorities())
        .thenAnswer(invocation ->
                List.of(
                        new SimpleGrantedAuthority("USER")
                )
        );

        when(userDetails.getUsername())
                .thenReturn(TEST_EMAIL);

        // Act
        jwtAuthenticationFilter.doFilter(
                request,
                response,
                filterChain
        );

        // Assert
        verify(jwtService)
                .isTokenValid(
                        TEST_TOKEN
                );

        verify(jwtService)
                .extractEmail(
                        TEST_TOKEN
                );

        verify(userDetailsService)
                .loadUserByUsername(
                        TEST_EMAIL
                );

        assertEquals(
                TEST_EMAIL,
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
                        .getName()
        );

        assertEquals(
                "USER",
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
                        .getAuthorities()
                        .iterator()
                        .next()
                        .getAuthority()
        );

        verify(filterChain)
                .doFilter(
                        request,
                        response
                );
    }

    @Test
    void shouldNotReplaceExistingAuthentication()
            throws ServletException, IOException {

        // Arrange
        request.addHeader(
                "Authorization",
                "Bearer " + TEST_TOKEN
        );

        when(jwtService.isTokenValid(
                TEST_TOKEN
        )).thenReturn(true);

        when(jwtService.extractEmail(
                TEST_TOKEN
        )).thenReturn(TEST_EMAIL);

        UsernamePasswordAuthenticationToken existingAuthentication =
                new UsernamePasswordAuthenticationToken(
                        "already-authenticated@example.com",
                        null,
                        List.of(
                                new SimpleGrantedAuthority("USER")
                        )
                );

        SecurityContextHolder
                .getContext()
                .setAuthentication(
                        existingAuthentication
                );

        // Act
        jwtAuthenticationFilter.doFilter(
                request,
                response,
                filterChain
        );

        // Assert
        verify(userDetailsService, never())
                .loadUserByUsername(
                        org.mockito.ArgumentMatchers.anyString()
                );

        assertEquals(
                "already-authenticated@example.com",
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
                        .getName()
        );

        verify(filterChain)
                .doFilter(
                        request,
                        response
                );
    }
}