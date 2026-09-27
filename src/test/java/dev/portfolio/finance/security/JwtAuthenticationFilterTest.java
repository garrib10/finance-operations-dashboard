package dev.portfolio.finance.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
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
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_TOKEN = "valid-jwt-token";

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
        jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtService, userDetailsService);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void assertUnauthenticatedAndContinued() throws ServletException, IOException {
        verify(filterChain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void shouldContinueFilterChainWhenAuthorizationHeaderIsMissing() throws Exception {
        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertUnauthenticatedAndContinued();
        verify(jwtService, never()).validate(anyString());
        assertNull(request.getAttribute(JwtAuthenticationFilter.ACCESS_TOKEN_EXPIRED_ATTRIBUTE));
    }

    @Test
    void shouldContinueFilterChainWhenAuthorizationHeaderIsNotBearer() throws Exception {
        request.addHeader("Authorization", "Basic abc123");

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertUnauthenticatedAndContinued();
        verify(jwtService, never()).validate(anyString());
    }

    @Test
    void shouldContinueFilterChainWhenTokenIsInvalid() throws Exception {
        request.addHeader("Authorization", "Bearer " + TEST_TOKEN);
        when(jwtService.validate(TEST_TOKEN)).thenReturn(AccessTokenValidation.invalid());

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        verify(jwtService).validate(TEST_TOKEN);
        verify(userDetailsService, never()).loadUserByUsername(anyString());
        assertUnauthenticatedAndContinued();
        assertNull(request.getAttribute(JwtAuthenticationFilter.ACCESS_TOKEN_EXPIRED_ATTRIBUTE));
    }

    @Test
    void shouldMarkExpiredTokenWithoutAuthenticating() throws Exception {
        request.addHeader("Authorization", "Bearer " + TEST_TOKEN);
        when(jwtService.validate(TEST_TOKEN)).thenReturn(AccessTokenValidation.expired());

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        verify(userDetailsService, never()).loadUserByUsername(anyString());
        assertUnauthenticatedAndContinued();
        assertEquals(Boolean.TRUE, request.getAttribute(JwtAuthenticationFilter.ACCESS_TOKEN_EXPIRED_ATTRIBUTE));
    }

    @Test
    void shouldSetAuthenticationWhenTokenIsValid() throws Exception {
        request.addHeader("Authorization", "Bearer " + TEST_TOKEN);
        when(jwtService.validate(TEST_TOKEN)).thenReturn(AccessTokenValidation.valid(TEST_EMAIL, 1L));
        when(userDetailsService.loadUserByUsername(TEST_EMAIL)).thenReturn(userDetails);
        when(userDetails.getAuthorities())
                .thenAnswer(invocation -> List.of(new SimpleGrantedAuthority("USER")));
        when(userDetails.getUsername()).thenReturn(TEST_EMAIL);

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        verify(jwtService).validate(TEST_TOKEN);
        verify(userDetailsService).loadUserByUsername(TEST_EMAIL);
        assertEquals(TEST_EMAIL, SecurityContextHolder.getContext().getAuthentication().getName());
        assertEquals("USER", SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().iterator().next().getAuthority());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldContinueUnauthenticatedWhenUserNoLongerExists() throws Exception {
        request.addHeader("Authorization", "Bearer " + TEST_TOKEN);
        when(jwtService.validate(TEST_TOKEN)).thenReturn(AccessTokenValidation.valid(TEST_EMAIL, 1L));
        when(userDetailsService.loadUserByUsername(TEST_EMAIL))
                .thenThrow(new UsernameNotFoundException("User not found"));

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertUnauthenticatedAndContinued();
    }

    @Test
    void shouldNotReplaceExistingAuthentication() throws Exception {
        request.addHeader("Authorization", "Bearer " + TEST_TOKEN);
        when(jwtService.validate(TEST_TOKEN)).thenReturn(AccessTokenValidation.valid(TEST_EMAIL, 1L));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "already-authenticated@example.com", null, List.of(new SimpleGrantedAuthority("USER"))));

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        verify(userDetailsService, never()).loadUserByUsername(anyString());
        assertEquals("already-authenticated@example.com",
                SecurityContextHolder.getContext().getAuthentication().getName());
        verify(filterChain).doFilter(request, response);
    }
}
