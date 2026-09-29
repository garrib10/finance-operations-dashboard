package dev.portfolio.finance.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import tools.jackson.databind.json.JsonMapper;

class RestAuthenticationEntryPointTest {

    private RestAuthenticationEntryPoint entryPoint;

    private MockHttpServletRequest request;

    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        JsonMapper jsonMapper =
                JsonMapper.builder()
                        .findAndAddModules()
                        .build();

        entryPoint =
                new RestAuthenticationEntryPoint(
                        jsonMapper
                );

        request =
                new MockHttpServletRequest();

        response =
                new MockHttpServletResponse();
    }

    @Test
    void shouldReturnUnauthorizedJsonResponseWhenAuthenticationIsRequired()
            throws Exception {

        // Arrange
        BadCredentialsException authenticationException =
                new BadCredentialsException(
                        "Authentication required"
                );

        // Act
        entryPoint.commence(
                request,
                response,
                authenticationException
        );

        // Assert
        assertEquals(
                401,
                response.getStatus()
        );

        assertEquals(
                "application/json",
                response.getContentType()
        );

        String responseBody =
                response.getContentAsString();

        assertTrue(
                responseBody.contains(
                        "\"status\":401"
                )
        );

        assertTrue(
                responseBody.contains(
                        "\"error\":\"Unauthorized\""
                )
        );

        assertTrue(
                responseBody.contains(
                        "\"message\":\"Authentication is required to access this resource\""
                )
        );
    }

    @Test
    void genericFailureUsesAuthenticationRequiredCode() throws Exception {
        entryPoint.commence(request, response, new BadCredentialsException("x"));

        assertTrue(response.getContentAsString().contains("\"code\":\"AUTHENTICATION_REQUIRED\""));
    }

    @Test
    void expiredAccessTokenUsesStableExpiredCode() throws Exception {
        request.setAttribute(JwtAuthenticationFilter.ACCESS_TOKEN_EXPIRED_ATTRIBUTE, Boolean.TRUE);

        entryPoint.commence(request, response, new BadCredentialsException("jwt parser detail"));

        String body = response.getContentAsString();
        assertEquals(401, response.getStatus());
        assertTrue(body.contains("\"code\":\"ACCESS_TOKEN_EXPIRED\""));
        assertTrue(body.contains("\"message\":\"Access token has expired\""));
        assertTrue(!body.contains("jwt parser detail") && !body.contains("Exception"));
    }
}
