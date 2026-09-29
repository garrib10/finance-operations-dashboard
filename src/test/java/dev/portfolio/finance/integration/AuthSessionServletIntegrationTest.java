package dev.portfolio.finance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.repository.RefreshTokenRepository;
import dev.portfolio.finance.repository.UserRepository;
import dev.portfolio.finance.security.RefreshTokenGenerator;

/**
 * Real Tomcat on a random port: proves wire-level Set-Cookie serialization, raw Cookie
 * header parsing, and CORS preflight, which MockMvc re-serializes or bypasses.
 */
@ActiveProfiles("test")
@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.frontend-urls=https://app.example,http://localhost:5173",
        "app.auth-session.refresh-cookie-secure=true",
        "spring.datasource.url=jdbc:h2:mem:auth_servlet;MODE=MySQL;NON_KEYWORDS=MONTH,YEAR;DB_CLOSE_DELAY=-1"})
class AuthSessionServletIntegrationTest {

    private static final String ORIGIN = "https://app.example";
    private static final String NAME = "__Secure-fintrack_refresh";
    private static final String PASSWORD = "River meadow lantern 42!";
    private static final Pattern ISSUED = Pattern.compile("^" + NAME + "=([A-Za-z0-9_-]{43}); Max-Age=(\\d+); "
            + "Expires=([A-Z][a-z]{2}, \\d{2} [A-Z][a-z]{2} \\d{4} \\d{2}:\\d{2}:\\d{2} GMT); "
            + "Path=/api/auth; Secure; HttpOnly; SameSite=Lax$");
    private static final String CLEARED = NAME + "=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; "
            + "Path=/api/auth; Secure; HttpOnly; SameSite=Lax";

    @Value("${local.server.port}") private int port;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder encoder;
    @Autowired private RefreshTokenRepository tokens;
    @Autowired private RefreshTokenGenerator generator;

    private final HttpClient client = HttpClient.newHttpClient();
    private User user;

    @BeforeEach
    void setUp() {
        user = users.saveAndFlush(new User("Wire", "User", "wire-" + UUID.randomUUID() + "@example.com",
                encoder.encode(PASSWORD)));
    }

    private HttpRequest.Builder request(String method, String path, String body) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(30))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body));
    }

    private HttpRequest.Builder protectedPost(String path, String body) {
        HttpRequest.Builder builder = request("POST", path, body)
                .header("Origin", ORIGIN).header("X-FinTrack-CSRF", "1");
        return body == null ? builder : builder.header("Content-Type", "application/json");
    }

    private HttpResponse<String> send(HttpRequest.Builder builder) throws Exception {
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> login() throws Exception {
        return send(protectedPost("/api/auth/login",
                "{\"email\":\"" + user.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}"));
    }

    private static String singleSetCookie(HttpResponse<String> response) {
        List<String> values = response.headers().allValues("Set-Cookie");
        assertThat(values).hasSize(1);
        return values.getFirst();
    }

    private static Matcher issued(HttpResponse<String> response) {
        Matcher matcher = ISSUED.matcher(singleSetCookie(response));
        assertThat(matcher.matches()).as("Set-Cookie has the exact approved attributes").isTrue();
        return matcher;
    }

    private boolean consumed(String raw) {
        return tokens.findByTokenHash(generator.hashPresentedToken(raw)).orElseThrow().isConsumed();
    }

    @Test
    void loginSerializesExactSecureCookieWithoutDomain() throws Exception {
        Instant before = Instant.now();
        HttpResponse<String> response = login();
        Instant after = Instant.now();

        assertThat(response.statusCode()).isEqualTo(200);
        Matcher cookie = issued(response);
        assertThat(singleSetCookie(response)).doesNotContainIgnoringCase("domain");
        // Remaining lifetime, rounded down, measured a moment after the session was created.
        assertThat(Long.parseLong(cookie.group(2))).isBetween(
                Duration.ofDays(30).toSeconds() - 5, Duration.ofDays(30).toSeconds());
        Instant expires = ZonedDateTime.parse(cookie.group(3),
                DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
                        .withZone(java.time.ZoneOffset.UTC)).toInstant();
        assertThat(expires).isBetween(before.plus(Duration.ofDays(30)).minusSeconds(2),
                after.plus(Duration.ofDays(30)).plusSeconds(2));
        assertThat(response.body()).doesNotContain(cookie.group(1)).contains("\"expiresIn\":300");
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).contains(ORIGIN);
        assertThat(response.headers().firstValue("Access-Control-Allow-Credentials")).contains("true");
    }

    @Test
    void refreshWithoutBearerRotatesAndReuseClearsWithMatchingScope() throws Exception {
        String first = issued(login()).group(1);

        HttpResponse<String> rotated = send(protectedPost("/api/auth/refresh", null)
                .header("Cookie", NAME + "=" + first));
        assertThat(rotated.statusCode()).isEqualTo(200);
        String second = issued(rotated).group(1);
        assertThat(second).isNotEqualTo(first);
        assertThat(Long.parseLong(issued(rotated).group(2))).isBetween(
                Duration.ofDays(30).toSeconds() - 5, Duration.ofDays(30).toSeconds());

        HttpResponse<String> reused = send(protectedPost("/api/auth/refresh", null)
                .header("Cookie", NAME + "=" + first));
        assertThat(reused.statusCode()).isEqualTo(401);
        assertThat(singleSetCookie(reused)).isEqualTo(CLEARED);
        assertThat(reused.body()).contains("\"code\":\"SESSION_EXPIRED\"");

        HttpResponse<String> successor = send(protectedPost("/api/auth/refresh", null)
                .header("Cookie", NAME + "=" + second));
        assertThat(successor.statusCode()).isEqualTo(401);
    }

    @Test
    void duplicateCookieHeaderValuesAreRejectedWithoutConsumingEither() throws Exception {
        String first = issued(login()).group(1);
        String second = issued(login()).group(1);

        HttpResponse<String> response = send(protectedPost("/api/auth/refresh", null)
                .header("Cookie", NAME + "=" + first + "; " + NAME + "=" + second));

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(singleSetCookie(response)).isEqualTo(CLEARED);
        assertThat(consumed(first)).isFalse();
        assertThat(consumed(second)).isFalse();
    }

    @Test
    void malformedCookieValuesAreTerminal(org.springframework.boot.test.system.CapturedOutput output)
            throws Exception {
        String raw = issued(login()).group(1);

        HttpResponse<String> quoted = send(protectedPost("/api/auth/refresh", null)
                .header("Cookie", NAME + "=\"" + raw + "\""));
        assertThat(quoted.statusCode()).as("DQUOTE-wrapped value is not unwrapped").isEqualTo(401);

        for (String cookie : List.of(NAME + "=" + raw + "=", NAME + "=",
                NAME + "=" + raw.substring(1), NAME + "=+" + raw.substring(1), NAME + "=/" + raw.substring(1),
                NAME + "=" + raw + " x", NAME + "=%" + raw.substring(1))) {
            HttpResponse<String> response = send(protectedPost("/api/auth/refresh", null).header("Cookie", cookie));
            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(singleSetCookie(response)).isEqualTo(CLEARED);
        }
        assertThat(consumed(raw)).isFalse();
        // Tomcat's cookie parser would otherwise log the rejected header verbatim.
        assertThat(output.getAll().contains(raw)).as("raw token absent from logs").isFalse();
    }

    @Test
    void requestProtectionIsEnforcedOnTheWire() throws Exception {
        String raw = issued(login()).group(1);
        String cookie = NAME + "=" + raw;

        List<HttpRequest.Builder> rejected = List.of(
                request("POST", "/api/auth/refresh", null).header("Origin", ORIGIN).header("Cookie", cookie),
                request("POST", "/api/auth/refresh", null).header("Origin", ORIGIN)
                        .header("X-FinTrack-CSRF", "2").header("Cookie", cookie),
                request("POST", "/api/auth/refresh", null).header("Origin", "null")
                        .header("X-FinTrack-CSRF", "1").header("Cookie", cookie),
                request("POST", "/api/auth/refresh", null).header("Origin", "https://app.example.evil.example")
                        .header("X-FinTrack-CSRF", "1").header("Cookie", cookie),
                request("POST", "/api/auth/logout", null).header("Origin", "https://evil.example")
                        .header("Referer", ORIGIN + "/").header("X-FinTrack-CSRF", "1").header("Cookie", cookie),
                request("POST", "/api/auth/logout", null).header("X-FinTrack-CSRF", "1").header("Cookie", cookie));

        for (HttpRequest.Builder builder : rejected) {
            HttpResponse<String> response = send(builder);
            assertThat(response.statusCode()).isEqualTo(403);
            assertThat(response.body()).contains("\"code\":\"REQUEST_FORBIDDEN\"")
                    .doesNotContain("evil", raw);
            assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
        }
        assertThat(consumed(raw)).isFalse();
    }

    @Test
    void refererFallbackWorksWhenOriginIsAbsent() throws Exception {
        HttpResponse<String> response = send(request("POST", "/api/auth/login",
                "{\"email\":\"" + user.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}")
                .header("Content-Type", "application/json")
                .header("Referer", ORIGIN + "/login").header("X-FinTrack-CSRF", "1"));

        assertThat(response.statusCode()).isEqualTo(200);
        issued(response);
    }

    @Test
    void logoutClearsWithMatchingScopeAndIsRepeatable() throws Exception {
        String raw = issued(login()).group(1);

        for (int i = 0; i < 2; i++) {
            HttpResponse<String> response = send(protectedPost("/api/auth/logout", null)
                    .header("Cookie", NAME + "=" + raw));
            assertThat(response.statusCode()).isEqualTo(204);
            assertThat(response.body()).isEmpty();
            assertThat(singleSetCookie(response)).isEqualTo(CLEARED);
        }
        assertThat(send(protectedPost("/api/auth/refresh", null).header("Cookie", NAME + "=" + raw))
                .statusCode()).isEqualTo(401);
    }

    @Test
    void preflightAllowsOnlyApprovedOriginsAndHeaders() throws Exception {
        HttpResponse<String> approved = send(request("OPTIONS", "/api/auth/refresh", null)
                .header("Origin", ORIGIN)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "x-fintrack-csrf,content-type"));
        assertThat(approved.statusCode()).isEqualTo(200);
        assertThat(approved.headers().firstValue("Access-Control-Allow-Origin")).contains(ORIGIN);
        assertThat(approved.headers().firstValue("Access-Control-Allow-Credentials")).contains("true");
        assertThat(approved.headers().firstValue("Access-Control-Allow-Headers").orElseThrow().toLowerCase(Locale.ROOT))
                .contains("x-fintrack-csrf", "content-type");
        assertThat(approved.headers().allValues("Access-Control-Allow-Origin")).doesNotContain("*");

        for (String origin : List.of("https://evil.example", "null", "https://app.example.evil.example")) {
            HttpResponse<String> denied = send(request("OPTIONS", "/api/auth/refresh", null)
                    .header("Origin", origin).header("Access-Control-Request-Method", "POST"));
            assertThat(denied.statusCode()).isEqualTo(403);
            assertThat(denied.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
        }

        HttpResponse<String> unknownHeader = send(request("OPTIONS", "/api/auth/refresh", null)
                .header("Origin", ORIGIN).header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "x-refresh-token"));
        assertThat(unknownHeader.statusCode()).isEqualTo(403);

        HttpResponse<String> business = send(request("OPTIONS", "/api/transactions", null)
                .header("Origin", ORIGIN).header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "authorization"));
        assertThat(business.statusCode()).isEqualTo(200);
        assertThat(business.headers().firstValue("Access-Control-Allow-Credentials")).isEmpty();
    }

    @Test
    void refreshCookieAloneCannotReachBusinessApis() throws Exception {
        String raw = issued(login()).group(1);

        for (String path : List.of("/api/auth/me", "/api/transactions", "/api/dashboard", "/api/account/photo")) {
            HttpResponse<String> response = send(request(path.endsWith("photo") ? "DELETE" : "GET", path, null)
                    .header("Origin", ORIGIN).header("X-FinTrack-CSRF", "1").header("Cookie", NAME + "=" + raw));
            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(response.body()).contains("\"code\":\"AUTHENTICATION_REQUIRED\"");
            assertThat(response.headers().allValues("Set-Cookie")).isEmpty();
        }
        assertThat(consumed(raw)).isFalse();
    }
}
