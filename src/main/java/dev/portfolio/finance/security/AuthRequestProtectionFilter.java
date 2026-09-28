package dev.portfolio.finance.security;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import dev.portfolio.finance.dto.error.ApiErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Login-CSRF and cookie-CSRF protection for the state-changing auth endpoints only.
 * Requires {@code X-FinTrack-CSRF: 1} and an exact allowlisted Origin (or, when Origin
 * is absent, an exact allowlisted Referer). Rejections happen before any controller
 * runs, so they never authenticate, rotate, revoke, or clear a cookie.
 */
public class AuthRequestProtectionFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-FinTrack-CSRF";
    public static final String HEADER_VALUE = "1";
    public static final String REQUEST_FORBIDDEN = "REQUEST_FORBIDDEN";

    private static final Logger log = LoggerFactory.getLogger(AuthRequestProtectionFilter.class);

    private static final RequestMatcher PROTECTED = new OrRequestMatcher(
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/auth/login"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/auth/refresh"),
            PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/auth/logout"));

    private final AllowedOrigins allowedOrigins;
    private final JsonMapper jsonMapper;

    public AuthRequestProtectionFilter(AllowedOrigins allowedOrigins, JsonMapper jsonMapper) {
        this.allowedOrigins = allowedOrigins;
        this.jsonMapper = jsonMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !PROTECTED.matches(request);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String rejection = rejectionCategory(request);
        if (rejection != null) {
            log.warn("auth.request_protection.rejected category={}", rejection);
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            jsonMapper.writeValue(response.getOutputStream(), new ApiErrorResponse(
                    LocalDateTime.now(), HttpStatus.FORBIDDEN.value(), "Forbidden",
                    "This request could not be verified", REQUEST_FORBIDDEN));
            return;
        }
        filterChain.doFilter(request, response);
    }

    /** Returns a fixed category (never a header value) or null when the request passes. */
    private String rejectionCategory(HttpServletRequest request) {
        List<String> csrf = values(request, HEADER_NAME);
        if (csrf.size() != 1 || !HEADER_VALUE.equals(csrf.getFirst())) {
            return "csrf_header";
        }
        List<String> origins = values(request, "Origin");
        if (!origins.isEmpty()) {
            // A present Origin is authoritative; Referer never overrides it.
            return origins.size() == 1 && allowedOrigins.isAllowedOrigin(origins.getFirst())
                    ? null : "origin";
        }
        List<String> referers = values(request, "Referer");
        return referers.size() == 1 && allowedOrigins.isAllowedReferer(referers.getFirst())
                ? null : "referer";
    }

    private static List<String> values(HttpServletRequest request, String name) {
        var headers = request.getHeaders(name);
        return headers == null ? List.of() : Collections.list(headers);
    }
}
