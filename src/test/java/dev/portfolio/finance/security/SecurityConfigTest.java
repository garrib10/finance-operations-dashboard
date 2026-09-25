package dev.portfolio.finance.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

@SpringBootTest(properties = {
        "app.frontend-urls=https://frontend.example,https://staging.example"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CorsConfigurationSource corsConfigurationSource;

    @Test
    void shouldAllowPublicHealthEndpointWithoutAuthentication()
            throws Exception {

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectProtectedEndpointWithoutAuthentication()
            throws Exception {

        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value(
                        "Authentication is required to access this resource"
                ));
    }

    @ParameterizedTest
    @MethodSource("protectedMutationEndpoints")
    void shouldRejectProtectedMutationWithoutAuthentication(
            HttpMethod method,
            String endpoint
    ) throws Exception {

        mockMvc.perform(request(method, endpoint)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/auth/login",
            "/api/auth/register"
    })
    void shouldReachPublicAuthenticationEndpointWithoutCsrfToken(
            String endpoint
    ) throws Exception {

        mockMvc.perform(post(endpoint)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldUseRestrictedCorsConfigurationWithoutCredentials() {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/transactions");

        CorsConfiguration configuration =
                corsConfigurationSource.getCorsConfiguration(request);

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowCredentials()).isFalse();

        assertThat(configuration.getAllowedOrigins())
                .containsExactly(
                        "https://frontend.example",
                        "https://staging.example"
                );

        assertThat(configuration.getAllowedHeaders())
                .containsExactly(
                        "Authorization",
                        "Content-Type"
                );

        assertThat(configuration.getAllowedMethods())
                .containsExactly(
                        "GET",
                        "POST",
                        "PUT",
                        "DELETE",
                        "OPTIONS"
                );
    }

    private static Stream<Arguments> protectedMutationEndpoints() {
        return Stream.of(
                Arguments.of(HttpMethod.POST, "/api/transactions"),
                Arguments.of(HttpMethod.PUT, "/api/transactions/1"),
                Arguments.of(HttpMethod.DELETE, "/api/transactions/1"),
                Arguments.of(HttpMethod.POST, "/api/budgets"),
                Arguments.of(HttpMethod.PUT, "/api/budgets/1"),
                Arguments.of(HttpMethod.DELETE, "/api/budgets/1"),
                Arguments.of(HttpMethod.POST, "/api/categories"),
                Arguments.of(HttpMethod.PUT, "/api/categories/1"),
                Arguments.of(HttpMethod.DELETE, "/api/categories/1")
        );
    }
}