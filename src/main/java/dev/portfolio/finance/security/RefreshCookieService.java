package dev.portfolio.finance.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import dev.portfolio.finance.config.AuthSessionProperties;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Single source of truth for refresh-cookie attributes. The cookie is always HttpOnly,
 * host-only (no Domain), and scoped to {@code /api/auth}. Header values contain the
 * raw token and must go only into {@code Set-Cookie}; never log them.
 */
@Component
public class RefreshCookieService {

    /** IMF-fixdate (RFC 9110), e.g. "Tue, 05 Oct 2026 12:00:00 GMT". */
    private static final DateTimeFormatter EXPIRES = DateTimeFormatter
            .ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
            .withZone(ZoneOffset.UTC);
    private static final String EPOCH = "Thu, 01 Jan 1970 00:00:00 GMT";

    private final AuthSessionProperties properties;
    private final Clock clock;

    public RefreshCookieService(AuthSessionProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public String cookieName() {
        return properties.refreshCookieName();
    }

    /** Lifetime is the remaining absolute session lifetime, never a fresh 30 days. */
    public String issue(String rawToken, Instant sessionExpiresAt) {
        Instant now = clock.instant();
        long maxAge = Math.max(0, Duration.between(now, sessionExpiresAt).getSeconds());
        return header(rawToken, maxAge, EXPIRES.format(now.plusSeconds(maxAge)));
    }

    /** Same name, path, Secure, and SameSite as issuance, so the browser replaces it. */
    public String clear() {
        return header("", 0, EPOCH);
    }

    /**
     * Reads the refresh cookie from the raw {@code Cookie} header(s) rather than the
     * container's parsed cookies, so the value is exactly what was sent: containers may
     * unwrap DQUOTE-quoted values, which would accept an alternate spelling. A name
     * present more than once (in one header or across several) is ambiguous and is
     * never resolved to one value.
     */
    public PresentedRefreshCookie read(HttpServletRequest request) {
        List<String> matches = new ArrayList<>();
        var headers = request.getHeaders("Cookie");
        while (headers != null && headers.hasMoreElements()) {
            for (String pair : headers.nextElement().split(";")) {
                String trimmed = pair.strip();
                int separator = trimmed.indexOf('=');
                if (separator > 0 && trimmed.substring(0, separator).equals(cookieName())) {
                    matches.add(trimmed.substring(separator + 1));
                }
            }
        }
        if (matches.isEmpty()) {
            return PresentedRefreshCookie.ABSENT;
        }
        if (matches.size() > 1) {
            return PresentedRefreshCookie.DUPLICATE;
        }
        return new PresentedRefreshCookie(PresentedRefreshCookie.State.PRESENT, matches.getFirst());
    }

    private String header(String value, long maxAge, String expires) {
        StringBuilder header = new StringBuilder()
                .append(cookieName()).append('=').append(value)
                .append("; Max-Age=").append(maxAge)
                .append("; Expires=").append(expires)
                .append("; Path=").append(AuthSessionProperties.REFRESH_COOKIE_PATH);
        if (properties.refreshCookieSecure()) {
            header.append("; Secure");
        }
        return header.append("; HttpOnly")
                .append("; SameSite=").append(properties.refreshCookieSameSite().attributeValue())
                .toString();
    }

    /** Presented cookie value; redacted in {@code toString()}. */
    public record PresentedRefreshCookie(State state, String value) {

        static final PresentedRefreshCookie ABSENT = new PresentedRefreshCookie(State.ABSENT, null);
        static final PresentedRefreshCookie DUPLICATE = new PresentedRefreshCookie(State.DUPLICATE, null);

        public enum State { ABSENT, DUPLICATE, PRESENT }

        @Override
        public String toString() {
            return "PresentedRefreshCookie[state=" + state + ", value redacted]";
        }
    }
}
