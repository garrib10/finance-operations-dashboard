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
                ))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
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

    @Test
    void shouldReachRegistrationWithoutCsrfTokenOrProtectionHeaders()
            throws Exception {

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReachLoginValidationOnlyWithRequestProtection()
            throws Exception {

        mockMvc.perform(post("/api/auth/login")
                        .header("Origin", "https://frontend.example")
                        .header("X-FinTrack-CSRF", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REQUEST_FORBIDDEN"));
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

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "/api/auth/login", "/api/auth/refresh", "/api/auth/logout"})
    void credentialedCorsIsLimitedToCookieAuthRoutesWithExactOrigins(String path) {
        CorsConfiguration configuration = corsConfigurationSource
                .getCorsConfiguration(new MockHttpServletRequest("POST", path));

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowCredentials()).isTrue();
        assertThat(configuration.getAllowedOrigins())
                .containsExactly("https://frontend.example", "https://staging.example")
                .doesNotContain("*");
        assertThat(configuration.getAllowedOriginPatterns()).isNullOrEmpty();
        assertThat(configuration.getAllowedHeaders())
                .containsExactly("Authorization", "Content-Type", "X-FinTrack-CSRF");
        assertThat(configuration.getAllowedMethods()).containsExactly("POST", "OPTIONS");
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "/api/auth/me", "/api/auth/register", "/api/account/profile", "/api/budgets"})
    void otherRoutesKeepUncredentialedCors(String path) {
        CorsConfiguration configuration = corsConfigurationSource
                .getCorsConfiguration(new MockHttpServletRequest("GET", path));

        assertThat(configuration.getAllowCredentials()).isFalse();
        assertThat(configuration.getAllowedHeaders()).doesNotContain("X-FinTrack-CSRF");
    }

    @Test
    void refreshAndLogoutNeedNoBearerTokenButStillRequireProtection() throws Exception {
        for (String path : new String[] {"/api/auth/refresh", "/api/auth/logout"}) {
            mockMvc.perform(post(path))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("REQUEST_FORBIDDEN"));
        }
        mockMvc.perform(post("/api/auth/refresh")
                        .header("Origin", "https://staging.example").header("X-FinTrack-CSRF", "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
        mockMvc.perform(post("/api/auth/logout")
                        .header("Origin", "https://staging.example").header("X-FinTrack-CSRF", "1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void protectionDoesNotApplyToBusinessMutations() throws Exception {
        mockMvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void swaggerRemainsReachableOutsideProduction() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
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