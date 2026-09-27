package dev.portfolio.finance.exception.account;

import dev.portfolio.finance.dto.error.ApiErrorResponse;
import java.time.LocalDateTime;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.HttpMediaTypeNotSupportedException;

/** Global multipart handling also covers parser failures before a controller is selected. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class ProfilePhotoExceptionHandler {
    @ExceptionHandler(InvalidProfilePhotoException.class)
    public ResponseEntity<ApiErrorResponse> invalid(InvalidProfilePhotoException ex) {
        HttpStatus status = switch (ex.getReason()) {
            case TOO_LARGE -> HttpStatus.CONTENT_TOO_LARGE;
            case UNSUPPORTED -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            default -> HttpStatus.BAD_REQUEST;
        };
        return error(status, ex.getMessage());
    }

    @ExceptionHandler(ProfilePhotoStorageException.class)
    public ResponseEntity<ApiErrorResponse> storage() {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "Profile photos are temporarily unavailable. Please try again later.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> tooLarge() {
        return error(HttpStatus.CONTENT_TOO_LARGE, "The photo or multipart request exceeds the allowed size.");
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiErrorResponse> multipart() {
        return error(HttpStatus.BAD_REQUEST, "Send exactly one JPEG or PNG file in the photo part of a valid multipart request.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> unsupported() {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Use the supported content type for this endpoint; photo uploads require multipart/form-data.");
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                LocalDateTime.now(), status.value(), status.getReasonPhrase(), message));
    }
}
