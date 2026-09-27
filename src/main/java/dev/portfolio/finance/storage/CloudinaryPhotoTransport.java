package dev.portfolio.finance.storage;

import com.cloudinary.ProgressCallback;
import com.cloudinary.strategies.AbstractUploaderStrategy;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.util.Timeout;

/** SDK extension point: fixed timeouts, no retries/redirects, bounded response bodies. */
final class CloudinaryPhotoTransport extends AbstractUploaderStrategy implements Closeable {
    private final CloseableHttpClient client;

    CloudinaryPhotoTransport() {
        this(HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setDefaultConnectionConfig(ConnectionConfig.custom()
                                .setConnectTimeout(Timeout.ofSeconds(10))
                                .setSocketTimeout(Timeout.ofSeconds(10)).build()).build())
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectionRequestTimeout(Timeout.ofSeconds(10))
                        .setResponseTimeout(Timeout.ofSeconds(10)).build())
                .disableAutomaticRetries().disableRedirectHandling().build());
    }

    CloudinaryPhotoTransport(CloseableHttpClient client) { this.client = client; }

    @Override
    @SuppressWarnings("rawtypes")
    public Map callApi(String action, Map<String, Object> params, Map options, Object file,
                       ProgressCallback callback) throws IOException {
        if ((!action.equals("upload") && !action.equals("destroy")) || callback != null
                || (file != null && !(file instanceof byte[]))) throw new IOException("Unsupported storage operation");
        uploader.signRequestParams(params, options);
        var multipart = MultipartEntityBuilder.create();
        params.forEach((key, value) -> multipart.addTextBody(key, value.toString(), ContentType.TEXT_PLAIN));
        if (file != null) multipart.addBinaryBody("file", (byte[])file, ContentType.IMAGE_JPEG, "photo.jpg");
        var request = new HttpPost(buildUploadUrl(action, options));
        request.setEntity(multipart.build());
        return client.execute(request, response -> {
            if (response.getEntity() == null) throw new IOException("Invalid storage response");
            try (var stream = response.getEntity().getContent()) {
                byte[] bytes = stream.readNBytes(65537);
                if (bytes.length > 65536) throw new IOException("Invalid storage response");
                return processResponse(false, response.getCode(), new String(bytes, StandardCharsets.UTF_8));
            }
        });
    }

    @Override
    public void close() throws IOException { client.close(); }
}
