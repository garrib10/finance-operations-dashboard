package dev.portfolio.finance.security;

import java.io.IOException;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import dev.portfolio.finance.dto.error.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    public static final String ACCESS_TOKEN_EXPIRED = "ACCESS_TOKEN_EXPIRED";
    public static final String AUTHENTICATION_REQUIRED = "AUTHENTICATION_REQUIRED";

    private final JsonMapper jsonMapper;

    public RestAuthenticationEntryPoint(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {

        boolean expired = Boolean.TRUE.equals(
                request.getAttribute(JwtAuthenticationFilter.ACCESS_TOKEN_EXPIRED_ATTRIBUTE));

        ApiErrorResponse errorResponse = new ApiErrorResponse(
                LocalDateTime.now(),
                HttpStatus.UNAUTHORIZED.value(),
                "Unauthorized",
                expired
                        ? "Access token has expired"
                        : "Authentication is required to access this resource",
                expired ? ACCESS_TOKEN_EXPIRED : AUTHENTICATION_REQUIRED
        );

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        jsonMapper.writeValue(
                response.getOutputStream(),
                errorResponse
        );
    }
}
