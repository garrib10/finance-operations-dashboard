package dev.portfolio.finance.config;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.error.ErrorAttributeOptions.Include;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

class ErrorResponseConfigTest {

    private final ErrorResponseConfig config = new ErrorResponseConfig();

    private Map<String, Object> attributesFor(int status, Throwable failure, ErrorAttributeOptions options) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/private/42");
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, status);
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/api/private/42");
        if (failure != null) request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, failure);
        return config.errorAttributes().getErrorAttributes(new ServletWebRequest(request), options);
    }

    @Test
    void matchesTheApiErrorShapeWithAFixedMessage() {
        Map<String, Object> body = attributesFor(500, new IllegalStateException("PRIVATE jdbc:mysql://db"),
                ErrorAttributeOptions.defaults());

        assertThat(body).containsOnlyKeys("timestamp", "status", "error", "message");
        assertThat(body).containsEntry("status", 500)
                .containsEntry("error", "Internal Server Error")
                .containsEntry("message", ErrorResponseConfig.SERVER_ERROR_MESSAGE);
    }

    @Test
    void neverAddsTheMessageTraceExceptionOrPathEvenWhenAsked() {
        ErrorAttributeOptions everything = ErrorAttributeOptions.of(
                Include.MESSAGE, Include.STACK_TRACE, Include.EXCEPTION, Include.BINDING_ERRORS, Include.PATH);

        Map<String, Object> body = attributesFor(500, new IllegalStateException("PRIVATE jdbc:mysql://db"), everything);

        assertThat(body).containsOnlyKeys("timestamp", "status", "error", "message");
        assertThat(body.toString()).doesNotContain("PRIVATE", "jdbc", "IllegalStateException", "/api/private");
    }

    @ParameterizedTest
    @CsvSource({
            "500, An unexpected error occurred. Please try again.",
            "503, An unexpected error occurred. Please try again.",
            "404, The requested resource was not found.",
            "400, The request could not be completed.",
            "405, The request could not be completed.",
    })
    void choosesTheMessageByStatusOnly(int status, String message) {
        assertThat(ErrorResponseConfig.messageFor(status)).isEqualTo(message);
        assertThat(attributesFor(status, null, ErrorAttributeOptions.defaults())).containsEntry("message", message);
    }

    @Test
    void treatsAMissingStatusAsAServerError() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");

        Map<String, Object> body = config.errorAttributes()
                .getErrorAttributes(new ServletWebRequest(request), ErrorAttributeOptions.defaults());

        assertThat(body.get("message")).isEqualTo(ErrorResponseConfig.SERVER_ERROR_MESSAGE);
    }
}
