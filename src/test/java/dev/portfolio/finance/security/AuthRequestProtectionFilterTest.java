package dev.portfolio.finance.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import jakarta.servlet.FilterChain;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(OutputCaptureExtension.class)
class AuthRequestProtectionFilterTest {

    private static final String APPROVED = "https://app.example";

    private final AuthRequestProtectionFilter filter = new AuthRequestProtectionFilter(
            AllowedOrigins.parse(APPROVED + ",http://localhost:5173"),
            JsonMapper.builder().findAndAddModules().build());

    private MockHttpServletResponse run(String method, String path, Consumer<MockHttpServletRequest> headers,
                                        FilterChain chain) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        headers.accept(request);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    private static Consumer<MockHttpServletRequest> valid() {
        return request -> {
            request.addHeader("Origin", APPROVED);
            request.addHeader("X-FinTrack-CSRF", "1");
        };
    }

    private void assertForbidden(MockHttpServletResponse response, FilterChain chain) throws Exception {
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(response.getContentAsString())
                .contains("\"status\":403", "\"error\":\"Forbidden\"", "\"code\":\"REQUEST_FORBIDDEN\"",
                        "\"message\":\"This request could not be verified\"");
        assertThat(response.getHeader("Set-Cookie")).isNull();
        verify(chain, never()).doFilter(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/auth/login", "/api/auth/refresh", "/api/auth/logout"})
    void passesExactOriginWithCustomHeader(String path) throws Exception {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = run("POST", path, valid(), chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/auth/login", "/api/auth/refresh", "/api/auth/logout"})
    void rejectsMissingCustomHeader(String path) throws Exception {
        FilterChain chain = mock(FilterChain.class);
        assertForbidden(run("POST", path, r -> r.addHeader("Origin", APPROVED), chain), chain);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "true", "", " 1", "1 ", "11", "yes"})
    void rejectsWrongCustomHeaderValue(String value) throws Exception {
        FilterChain chain = mock(FilterChain.class);
        assertForbidden(run("POST", "/api/auth/refresh", r -> {
            r.addHeader("Origin", APPROVED);
            r.addHeader("X-FinTrack-CSRF", value);
        }, chain), chain);
    }

    @Test
    void rejectsRepeatedCustomHeader() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        assertForbidden(run("POST", "/api/auth/refresh", r -> {
            r.addHeader("Origin", APPROVED);
            r.addHeader("X-FinTrack-CSRF", "1");
            r.addHeader("X-FinTrack-CSRF", "1");
        }, chain), chain);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "", "*", "https://evil.example", "https://app.example.evil.example",
            "https://evil.app.example", "http://app.example", "https://app.example:8443",
            "https://app.example/", "not-an-origin", "https://app.example, https://evil.example"})
    void rejectsUnapprovedOrMalformedOrigin(String origin) throws Exception {
        FilterChain chain = mock(FilterChain.class);
        assertForbidden(run("POST", "/api/auth/login", r -> {
            r.addHeader("Origin", origin);
            r.addHeader("X-FinTrack-CSRF", "1");
        }, chain), chain);
    }

    @Test
    void rejectsMultipleOriginHeadersEvenWhenOneIsApproved() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        assertForbidden(run("POST", "/api/auth/login", r -> {
            r.addHeader("Origin", APPROVED);
            r.addHeader("Origin", APPROVED);
            r.addHeader("X-FinTrack-CSRF", "1");
        }, chain), chain);
    }

    @Test
    void acceptsApprovedRefererOnlyWhenOriginIsAbsent() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = run("POST", "/api/auth/logout", r -> {
            r.addHeader("Referer", APPROVED + "/settings");
            r.addHeader("X-FinTrack-CSRF", "1");
        }, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(any(), any());
    }

    @Test
    void invalidOriginIsNotOverriddenByValidReferer() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        assertForbidden(run("POST", "/api/auth/refresh", r -> {
            r.addHeader("Origin", "https://evil.example");
            r.addHeader("Referer", APPROVED + "/dashboard");
            r.addHeader("X-FinTrack-CSRF", "1");
        }, chain), chain);
    }

    @Test
    void nullOriginIsNotOverriddenByValidReferer() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        assertForbidden(run("POST", "/api/auth/refresh", r -> {
            r.addHeader("Origin", "null");
            r.addHeader("Referer", APPROVED + "/dashboard");
            r.addHeader("X-FinTrack-CSRF", "1");
        }, chain), chain);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "https://evil.example/", "https://app.example.evil.example/x", "/relative"})
    void rejectsMissingOrUnapprovedReferer(String referer) throws Exception {
        FilterChain chain = mock(FilterChain.class);
        assertForbidden(run("POST", "/api/auth/login", r -> {
            if (!referer.isEmpty()) {
                r.addHeader("Referer", referer);
            }
            r.addHeader("X-FinTrack-CSRF", "1");
        }, chain), chain);
    }

    @Test
    void rejectsMultipleRefererHeaders() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        assertForbidden(run("POST", "/api/auth/login", r -> {
            r.addHeader("Referer", APPROVED + "/a");
            r.addHeader("Referer", APPROVED + "/b");
            r.addHeader("X-FinTrack-CSRF", "1");
        }, chain), chain);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET /api/auth/refresh", "OPTIONS /api/auth/login", "POST /api/auth/register",
            "GET /api/auth/me", "POST /api/transactions", "POST /api/account/password", "PUT /api/account/photo"})
    void leavesOtherRoutesAndMethodsToExistingHandling(String route) throws Exception {
        String[] parts = route.split(" ");
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = run(parts[0], parts[1], r -> { }, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(any(), any());
    }

    @Test
    void rejectionsDoNotEchoOrLogHeaderValues(CapturedOutput output) throws Exception {
        FilterChain chain = mock(FilterChain.class);
        String probe = "https://origin-probe-value.example";
        MockHttpServletResponse response = run("POST", "/api/auth/login", r -> {
            r.addHeader("Origin", probe);
            r.addHeader("X-FinTrack-CSRF", "1");
        }, chain);

        assertThat(response.getContentAsString()).doesNotContain(probe);
        assertThat(output.getAll()).contains("auth.request_protection.rejected category=origin")
                .doesNotContain(probe);
    }
}
