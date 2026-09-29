package dev.portfolio.finance.support;

import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Headers a browser on the approved frontend origin sends to protected auth endpoints. */
public final class AuthRequests {

    /** Matches the default app.frontend-urls used by the test profile. */
    public static final String ORIGIN = "http://localhost:5173";
    public static final String CSRF_HEADER = "X-FinTrack-CSRF";
    public static final String CSRF_VALUE = "1";

    private AuthRequests() {
    }

    public static MockHttpServletRequestBuilder protectedAuth(MockHttpServletRequestBuilder builder) {
        return builder.header("Origin", ORIGIN).header(CSRF_HEADER, CSRF_VALUE);
    }
}
