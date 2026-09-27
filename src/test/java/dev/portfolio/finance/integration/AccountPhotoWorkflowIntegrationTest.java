package dev.portfolio.finance.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import dev.portfolio.finance.entity.User;
import dev.portfolio.finance.repository.UserRepository;
import dev.portfolio.finance.security.JwtService;
import dev.portfolio.finance.storage.ProfilePhotoStorage;
import dev.portfolio.finance.support.ProfilePhotoImages;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.profile-photo.enabled=true", "app.profile-photo.cloud-name=test-cloud",
        "app.profile-photo.api-key=test-key", "app.profile-photo.api-secret=test-secret",
        "app.profile-photo.key-prefix=fintrack/test/profile-photos",
        "spring.datasource.url=jdbc:h2:mem:photo_workflow;MODE=MySQL;NON_KEYWORDS=MONTH,YEAR;DB_CLOSE_DELAY=-1",
        "logging.level.org.springframework.web=DEBUG"})
class AccountPhotoWorkflowIntegrationTest {
    @Value("${local.server.port}") private int port;
    @MockitoBean private ProfilePhotoStorage storage;
    @MockitoSpyBean private UserRepository users;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JwtService jwt;
    @Autowired private JsonMapper mapper;
    private final HttpClient client = HttpClient.newHttpClient();
    private User a;
    private User b;
    private String token;
    private static final String PASSWORD = "Meadow river lantern 73!";
    private static final String BOUNDARY = "fintrack-test-boundary";

    @BeforeEach
    void setup() {
        String suffix = UUID.randomUUID().toString();
        a = users.saveAndFlush(new User("A", "User", "a-" + suffix + "@example.com", encoder.encode(PASSWORD)));
        b = users.saveAndFlush(new User("B", "User", "b-" + suffix + "@example.com", encoder.encode(PASSWORD)));
        token = jwt.generateToken(a);
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return null;
        }).when(storage).store(any(), any());
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return null;
        }).when(storage).delete(any());
    }

    private HttpResponse<String> request(String method, String path, String type, byte[] body, String bearer) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(java.time.Duration.ofSeconds(30)).header("Content-Type", type)
                .method(method, HttpRequest.BodyPublishers.ofByteArray(body));
        if (bearer != null) builder.header("Authorization", "Bearer " + bearer);
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private byte[] multipart(String[] names, byte[][] contents) throws Exception {
        var out = new ByteArrayOutputStream();
        for (int i = 0; i < names.length; i++) {
            out.write(("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"" + names[i]
                    + "\"; filename=\"PRIVATE_FILENAME.png\"\r\nContent-Type: application/octet-stream\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            out.write(contents[i]); out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        out.write(("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private HttpResponse<String> upload(byte[] bytes) throws Exception {
        return request("PUT", "/api/account/photo", "multipart/form-data; boundary=" + BOUNDARY,
                multipart(new String[]{"photo"}, new byte[][]{bytes}), token);
    }

    private HttpResponse<String> json(String method, String path, Object body) throws Exception {
        return request(method, path, "application/json", mapper.writeValueAsBytes(body), token);
    }

    private String key() { return users.findById(a.getId()).orElseThrow().getProfilePhotoKey(); }

    @ParameterizedTest
    @ValueSource(strings = {"jpeg", "png"})
    void uploadReplacementRemovalAndCanonicalAccountWorkflows(String format) throws Exception {
        byte[] original = ProfilePhotoImages.image(format, 40, 20);
        var first = upload(original);
        assertThat(first.statusCode()).isEqualTo(200);
        String firstKey = key();
        assertThat(firstKey).matches("fintrack/test/profile-photos/[0-9a-f-]{36}");
        String url = mapper.readTree(first.body()).get("profilePhotoUrl").stringValue();
        assertThat(url).isEqualTo("https://res.cloudinary.com/test-cloud/image/upload/v1/" + firstKey + ".jpg");
        assertThat(first.body()).doesNotContain("profilePhotoKey", "passwordHash", "PRIVATE_FILENAME", "test-secret");
        verify(storage).store(eq(firstKey), argThat(bytes -> (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216));
        assertThat(json("GET", "/api/auth/me", Map.of()).body()).contains(url);
        var profile = json("PUT", "/api/account/profile", Map.of("firstName", "New", "lastName", "Name", "displayName", "Display"));
        assertThat(profile.statusCode()).isEqualTo(200); assertThat(profile.body()).contains(url);
        var preferences = json("PUT", "/api/account/preferences", Map.of("dateFormat", "ISO", "transactionPageSize", 25));
        assertThat(preferences.statusCode()).isEqualTo(200); assertThat(preferences.body()).contains(url);
        var password = json("POST", "/api/account/password", Map.of("currentPassword", PASSWORD, "newPassword", "Another meadow river 84!"));
        assertThat(password.statusCode()).isEqualTo(204); assertThat(key()).isEqualTo(firstKey);
        var login = json("POST", "/api/auth/login", Map.of("email", a.getEmail(), "password", "Another meadow river 84!"));
        assertThat(login.statusCode()).isEqualTo(200);
        token = mapper.readTree(login.body()).get("accessToken").stringValue();
        assertThat(json("GET", "/api/auth/me", Map.of()).body()).contains(url);
        assertThat(upload(original).statusCode()).isEqualTo(200);
        String replacement = key(); assertThat(replacement).isNotEqualTo(firstKey);
        verify(storage).delete(firstKey);
        // Extra client identifiers are ignored; only the token's account is removed.
        var removed = json("DELETE", "/api/account/photo?userId=" + b.getId(), Map.of("email", b.getEmail(), "key", "foreign"));
        assertThat(removed.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(removed.body()).get("profilePhotoUrl").isNull()).isTrue();
        assertThat(key()).isNull(); verify(storage).delete(replacement);
        assertThat(json("DELETE", "/api/account/photo", Map.of()).statusCode()).isEqualTo(200);
        verify(storage, times(1)).delete(replacement);
        assertThat(users.findById(b.getId()).orElseThrow().getProfilePhotoKey()).isNull();
    }

    @Test
    void realServletAcceptsBoundarySizeAndRejectsFileAndTotalRequestLimits() throws Exception {
        byte[] jpeg = ProfilePhotoImages.image("jpeg", 20, 20);
        assertThat(upload(Arrays.copyOf(jpeg, 2097152)).statusCode()).isEqualTo(200);
        String previous = key();
        assertError(upload(Arrays.copyOf(jpeg, 2097153)), 413);
        assertError(request("PUT", "/api/account/photo", "multipart/form-data; boundary=" + BOUNDARY,
                multipart(new String[]{"photo", "extra"}, new byte[][]{new byte[1700000], new byte[1700000]}), token), 413);
        assertThat(key()).isEqualTo(previous);
        verify(storage, times(1)).store(any(), any());
    }

    @Test
    void malformedMissingEmptyDuplicateAndUnexpectedPartsAreRejected() throws Exception {
        assertError(request("PUT", "/api/account/photo", "multipart/form-data", new byte[]{1,2,3}, token), 400);
        assertError(request("PUT", "/api/account/photo", "multipart/form-data; boundary=" + BOUNDARY,
                multipart(new String[0], new byte[0][]), token), 400);
        assertError(upload(new byte[0]), 400);
        byte[] image = ProfilePhotoImages.image("png", 20, 20);
        for (String[] names : new String[][]{{"photo", "photo"}, {"photo", "userId"}, {"url"}}) {
            byte[][] bodies = new byte[names.length][]; Arrays.fill(bodies, image);
            assertError(request("PUT", "/api/account/photo", "multipart/form-data; boundary=" + BOUNDARY,
                    multipart(names, bodies), token), 400);
        }
        assertError(request("PUT", "/api/account/photo?email=" + b.getEmail(), "multipart/form-data; boundary=" + BOUNDARY,
                multipart(new String[]{"photo"}, new byte[][]{image}), token), 400);
        assertError(json("PUT", "/api/account/photo", Map.of("url", "https://example.com/a.jpg")), 415);
        verify(storage, never()).store(any(), any());
    }

    @Test
    void invalidProviderAndPersistenceFailuresPreservePreviousPhoto(CapturedOutput output) throws Exception {
        byte[] image = ProfilePhotoImages.image("png", 20, 20);
        assertThat(upload(image).statusCode()).isEqualTo(200);
        String previous = key();
        assertError(upload("<svg/>".getBytes(StandardCharsets.UTF_8)), 415);
        assertError(upload(new byte[]{(byte)255, (byte)216, (byte)255}), 400);
        doThrow(new IllegalStateException("PRIVATE_PROVIDER")).when(storage).store(any(), any());
        assertError(upload(image), 503); assertThat(key()).isEqualTo(previous);
        doNothing().when(storage).store(any(), any());
        doThrow(new IllegalStateException("PRIVATE_SQL")).when(users).saveAndFlush(argThat(u -> u.getId().equals(a.getId())));
        assertError(upload(image), 500); assertThat(key()).isEqualTo(previous);
        verify(storage).delete(argThat(k -> !k.equals(previous)));
        assertThat(output).doesNotContain("PRIVATE_PROVIDER", "PRIVATE_SQL", "PRIVATE_FILENAME");
    }

    @Test
    void cleanupFailureKeepsCommittedReplacementAndRemoval(CapturedOutput output) throws Exception {
        byte[] image = ProfilePhotoImages.image("png", 20, 20);
        assertThat(upload(image).statusCode()).isEqualTo(200);
        String old = key();
        doThrow(new IllegalStateException("PRIVATE_CLEANUP")).when(storage).delete(any());
        assertThat(upload(image).statusCode()).isEqualTo(200); assertThat(key()).isNotEqualTo(old);
        assertThat(json("DELETE", "/api/account/photo", Map.of()).statusCode()).isEqualTo(200);
        assertThat(key()).isNull();
        assertThat(output).contains("category=provider_failure").doesNotContain("PRIVATE_CLEANUP");
    }

    @Test
    void authenticationAndOwnershipUseOnlyBearerPrincipal() throws Exception {
        byte[] image = ProfilePhotoImages.image("png", 20, 20);
        for (String bearer : new String[]{null, "invalid-token"}) {
            assertError(request("PUT", "/api/account/photo", "multipart/form-data; boundary=" + BOUNDARY,
                    multipart(new String[]{"photo"}, new byte[][]{image}), bearer), 401);
            assertError(request("DELETE", "/api/account/photo", "application/json", new byte[0], bearer), 401);
        }
        assertThat(upload(image).statusCode()).isEqualTo(200);
        String aKey = key(); token = jwt.generateToken(b);
        assertThat(upload(image).statusCode()).isEqualTo(200);
        String bKey = users.findById(b.getId()).orElseThrow().getProfilePhotoKey();
        assertThat(bKey).isNotEqualTo(aKey); assertThat(key()).isEqualTo(aKey);
        assertThat(json("DELETE", "/api/account/photo", Map.of("userId", a.getId(), "key", aKey)).statusCode()).isEqualTo(200);
        assertThat(key()).isEqualTo(aKey); verify(storage, never()).delete(aKey);
    }

    @Test
    void registrationAndExistingUsersHaveNullPhoto() throws Exception {
        assertThat(mapper.readTree(json("GET", "/api/auth/me", Map.of()).body()).get("profilePhotoUrl").isNull()).isTrue();
        var registration = json("POST", "/api/auth/register", Map.of("firstName", "New", "lastName", "User",
                "email", UUID.randomUUID() + "@example.com", "password", PASSWORD));
        assertThat(registration.statusCode()).isEqualTo(201);
        assertThat(mapper.readTree(registration.body()).get("profilePhotoUrl").isNull()).isTrue();
    }

    @Test
    void failedRemovalRollsBackAndDoesNotDeleteActiveObject() throws Exception {
        assertThat(upload(ProfilePhotoImages.image("png", 20, 20)).statusCode()).isEqualTo(200);
        String previous = key();
        doThrow(new IllegalStateException("PRIVATE_SQL")).when(users).saveAndFlush(argThat(u -> u.getId().equals(a.getId())));
        assertError(json("DELETE", "/api/account/photo", Map.of()), 500);
        assertThat(key()).isEqualTo(previous);
        verify(storage, never()).delete(any());
    }

    @Test
    void concurrentReplacementsCleanOnlySupersededObject() throws Exception {
        byte[] image = ProfilePhotoImages.image("png", 20, 20);
        var uploads = new java.util.concurrent.CountDownLatch(2);
        var stored = java.util.concurrent.ConcurrentHashMap.<String>newKeySet();
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            stored.add(invocation.getArgument(0));
            uploads.countDown();
            assertThat(uploads.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(storage).store(any(), any());
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> upload(image));
            var second = executor.submit(() -> upload(image));
            assertThat(first.get(20, java.util.concurrent.TimeUnit.SECONDS).statusCode()).isEqualTo(200);
            assertThat(second.get(20, java.util.concurrent.TimeUnit.SECONDS).statusCode()).isEqualTo(200);
        }
        assertThat(stored).hasSize(2).contains(key());
        verify(storage, never()).delete(key());
        stored.remove(key());
        verify(storage).delete(stored.iterator().next());
    }

    private void assertError(HttpResponse<String> response, int status) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(mapper.readTree(response.body()).get("status").intValue()).isEqualTo(status);
        assertThat(response.body()).doesNotContain("profilePhotoKey", "PRIVATE_", "stackTrace", "test-secret");
    }
}
