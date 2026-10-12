package dev.portfolio.finance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.monitoring.CorrelationIdFilter;
import dev.portfolio.finance.repository.UserRepository;
import dev.portfolio.finance.security.JwtService;

/**
 * An unhandled exception on the real server (Tomcat, not MockMvc): logged once, by the
 * application, with the correlation ID and a stack trace whose messages are removed. Tomcat's
 * own copy, which would print the message and has no ID, is switched off. The client gets a
 * 500 with a fixed, safe body (issue #123), not the 401 the secured /error page used to give.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class ServerErrorLoggingIntegrationTest {

    private static final String PRIVATE_DETAIL = "sentinel-private-detail-8r2 jdbc:mysql://db.internal amount=99.10";

    @TestConfiguration
    static class Config {
        @RestController
        static class FailingController {
            @GetMapping("/api/server-error-test/failure/{id}")
            String fail() {
                throw new IllegalStateException(PRIVATE_DETAIL, new IllegalArgumentException(PRIVATE_DETAIL));
            }
        }
    }

    @Value("${local.server.port}") private int port;
    @Autowired private UserRepository users;
    @Autowired private JwtService jwtService;

    private HttpResponse<String> get(String path, String bearer) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header(CorrelationIdFilter.HEADER, "trace-tomcat-1");
        if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String token() {
        User user = users.save(new User("Server", "Error", "server-error-" + UUID.randomUUID() + "@example.com", "hash"));
        return jwtService.generateToken(user);
    }

    @Test
    void answersAnUnhandledExceptionWithA500AndASafeBody() throws Exception {
        HttpResponse<String> response = get("/api/server-error-test/failure/42", token());

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.headers().firstValue(CorrelationIdFilter.HEADER)).contains("trace-tomcat-1");
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(type -> assertThat(type).startsWith("application/json"));
        assertThat(response.body()).contains("\"status\":500", "\"error\":\"Internal Server Error\"",
                "\"message\":\"An unexpected error occurred. Please try again.\"", "\"timestamp\"");
        // Nothing about the failure or the request reaches the client.
        assertThat(response.body()).doesNotContain("sentinel-private-detail", "jdbc:", "99.10", "IllegalStateException",
                "exception", "trace", "path", "/api/server-error-test");
    }

    @Test
    void keepsTheErrorPageItselfBehindAuthentication() throws Exception {
        HttpResponse<String> direct = get("/error", null);

        assertThat(direct.statusCode()).isEqualTo(401);
        assertThat(direct.body()).contains("\"status\":401").doesNotContain("trace", "exception");
    }

    @Test
    void stillAnswersUnauthenticatedAndMissingRoutesAsBefore() throws Exception {
        assertThat(get("/api/server-error-test/failure/42", null).statusCode()).isEqualTo(401);
        assertThat(get("/api/transactions/999999999", token()).statusCode()).isEqualTo(404);

        // A signed-in request to a route that does not exist now gets a 404 too.
        HttpResponse<String> missing = get("/api/no-such-route", token());
        assertThat(missing.statusCode()).isEqualTo(404);
        assertThat(missing.body()).contains("\"message\":\"The requested resource was not found.\"")
                .doesNotContain("/api/no-such-route");
    }

    @Test
    void logsTheFailureOnceWithItsIdAndCodeLocationsButNeverItsMessage(CapturedOutput output) throws Exception {
        HttpResponse<String> response = get("/api/server-error-test/failure/42", token());

        String logs = output.getAll();
        assertThat(response.headers().firstValue(CorrelationIdFilter.HEADER)).contains("trace-tomcat-1");
        assertThat(logs).doesNotContain("sentinel-private-detail", "jdbc:mysql", "99.10");
        assertThat(logs).doesNotContain("Servlet.service()"); // Tomcat's own copy is off.
        assertThat(logs).contains("[trace-tomcat-1]", "http.request.failed",
                "route=/api/server-error-test/failure/{id}", "status=500", "exception=IllegalArgumentException");
        // The stack trace keeps class names and code locations so the failure can be found.
        assertThat(logs).contains("java.lang.IllegalStateException (message removed)",
                "java.lang.IllegalArgumentException (message removed)", "ServerErrorLoggingIntegrationTest");
        assertThat(logs.split("http.request.failed", -1)).hasSize(2);
    }
}
