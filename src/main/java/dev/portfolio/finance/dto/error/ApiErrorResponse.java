package dev.portfolio.finance.dto.error;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonInclude;

/** {@code code} is a stable machine-readable value; omitted when a response has none. */
public record ApiErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        @JsonInclude(JsonInclude.Include.NON_NULL) String code
) {
    public ApiErrorResponse(LocalDateTime timestamp, int status, String error, String message) {
        this(timestamp, status, error, message, null);
    }
}
