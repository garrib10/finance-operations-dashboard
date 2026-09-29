package dev.portfolio.finance.storage;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CloudinaryPhotoTransportTest {
    private final CloseableHttpClient client = mock(CloseableHttpClient.class);
    private final CloudinaryPhotoTransport transport = new CloudinaryPhotoTransport(client);
    private final Uploader uploader = new Uploader(new Cloudinary(Map.of("cloud_name", "test-cloud",
            "api_key", "test-key", "api_secret", "test-secret")), transport);

    @Test
    @SuppressWarnings("unchecked")
    void sendsSignedMultipartToFixedApiAndClosesResponseAndClient() throws Exception {
        when(client.execute(any(ClassicHttpRequest.class), any(HttpClientResponseHandler.class))).thenAnswer(invocation -> {
            ClassicHttpRequest request = invocation.getArgument(0);
            assertThat(request.getUri().toString()).isEqualTo("https://api.cloudinary.com/v1_1/test-cloud/image/upload");
            var body = new ByteArrayOutputStream(); request.getEntity().writeTo(body);
            assertThat(body.toString(java.nio.charset.StandardCharsets.UTF_8))
                    .contains("photo.jpg", "image/jpeg", "signature", "test-key").doesNotContain("test-secret");
            var response = new BasicClassicHttpResponse(200);
            response.setEntity(new StringEntity("{\"public_id\":\"test\"}"));
            return ((HttpClientResponseHandler<?>)invocation.getArgument(1)).handleResponse(response);
        });
        assertThat(uploader.upload(new byte[]{1,2}, Map.of("resource_type", "image"))).containsEntry("public_id", "test");
        verify(client, times(1)).execute(any(ClassicHttpRequest.class), any(HttpClientResponseHandler.class));
        transport.close(); verify(client).close();
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 503})
    void boundsProviderResponsesAndDoesNotRetry(int status) throws Exception {
        when(client.execute(any(ClassicHttpRequest.class), any(HttpClientResponseHandler.class))).thenAnswer(invocation -> {
            var response = new BasicClassicHttpResponse(status);
            response.setEntity(new StringEntity(status == 200 ? "x".repeat(65537) : "unavailable"));
            return ((HttpClientResponseHandler<?>)invocation.getArgument(1)).handleResponse(response);
        });
        assertThatThrownBy(() -> uploader.destroy("test", Map.of("resource_type", "image"))).isInstanceOf(Exception.class);
        verify(client, times(1)).execute(any(ClassicHttpRequest.class), any(HttpClientResponseHandler.class));
    }

    @Test
    void rejectsMissingResponseEntity() throws Exception {
        when(client.execute(any(ClassicHttpRequest.class), any(HttpClientResponseHandler.class))).thenAnswer(invocation ->
                ((HttpClientResponseHandler<?>)invocation.getArgument(1)).handleResponse(new BasicClassicHttpResponse(200)));
        assertThatThrownBy(() -> uploader.destroy("test", Map.of())).isInstanceOf(IOException.class);
    }

    @Test
    void transportRejectsUnsupportedOperationsRemoteUrlsAndCallbacks() {
        assertThatThrownBy(() -> transport.callApi("fetch", Map.of(), Map.of(), null, null)).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> transport.callApi("upload", Map.of(), Map.of(), "https://evil.example", null)).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> transport.callApi("upload", Map.of(), Map.of(), new byte[1], mock(com.cloudinary.ProgressCallback.class))).isInstanceOf(IOException.class);
        verifyNoInteractions(client);
    }
}
