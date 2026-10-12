package dev.portfolio.finance.config;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.webmvc.error.DefaultErrorAttributes;
import org.springframework.boot.webmvc.error.ErrorAttributes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.WebRequest;

/**
 * The body Spring Boot's /error page returns for failures no controller handled, such as an
 * unhandled exception (issue #123). It matches ApiErrorResponse (timestamp, status, error,
 * message) so clients parse every error the same way, and its message is fixed text: never
 * the exception's message, its class, a stack trace, or the request path.
 */
@Configuration
public class ErrorResponseConfig {

    static final String SERVER_ERROR_MESSAGE = "An unexpected error occurred. Please try again.";
    static final String NOT_FOUND_MESSAGE = "The requested resource was not found.";
    static final String CLIENT_ERROR_MESSAGE = "The request could not be completed.";

    @Bean
    public ErrorAttributes errorAttributes() {
        return new DefaultErrorAttributes() {
            @Override
            public Map<String, Object> getErrorAttributes(WebRequest request, ErrorAttributeOptions options) {
                // Defaults only (status and reason phrase); the requested options are ignored so
                // no setting can ever add the message, trace, or binding errors.
                Map<String, Object> defaults = super.getErrorAttributes(request, ErrorAttributeOptions.defaults());
                int status = defaults.get("status") instanceof Integer code ? code : 500;

                Map<String, Object> body = new LinkedHashMap<>();
                body.put("timestamp", LocalDateTime.now());
                body.put("status", status);
                body.put("error", defaults.getOrDefault("error", "Internal Server Error"));
                body.put("message", messageFor(status));
                return body;
            }
        };
    }

    static String messageFor(int status) {
        if (status >= 500) return SERVER_ERROR_MESSAGE;
        if (status == 404) return NOT_FOUND_MESSAGE;
        return CLIENT_ERROR_MESSAGE;
    }
}
