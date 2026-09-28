package dev.portfolio.finance.security;

import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Bearer access tokens only; refresh cookies never authenticate a request. */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Request attribute read by the entry point; set only for an expired, validly signed token. */
    public static final String ACCESS_TOKEN_EXPIRED_ATTRIBUTE =
            JwtAuthenticationFilter.class.getName() + ".ACCESS_TOKEN_EXPIRED";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            CustomUserDetailsService userDetailsService
    ) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        AccessTokenValidation result = jwtService.validate(authHeader.substring(7));

        if (result.status() == AccessTokenValidation.Status.EXPIRED) {
            request.setAttribute(ACCESS_TOKEN_EXPIRED_ATTRIBUTE, Boolean.TRUE);
        }

        if (result.isValid()
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            UserDetails userDetails;
            try {
                userDetails = userDetailsService.loadUserByUsername(result.email());
            } catch (UsernameNotFoundException ex) {
                SecurityContextHolder.clearContext();
                filterChain.doFilter(request, response);
                return;
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );

            SecurityContextHolder
                    .getContext()
                    .setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }
}
