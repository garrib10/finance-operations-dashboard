package dev.portfolio.finance.exception.account;

import java.time.LocalDateTime;
import java.util.Map;
import tools.jackson.databind.exc.MismatchedInputException;
import dev.portfolio.finance.controller.AccountController;
import dev.portfolio.finance.dto.error.ApiErrorResponse;
import dev.portfolio.finance.dto.error.ValidationErrorResponse;
import dev.portfolio.finance.exception.GlobalExceptionHandler;
import dev.portfolio.finance.exception.auth.InvalidCredentialsException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Keep fallback handling local to account endpoints; never echo exception details. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = AccountController.class)
public class AccountExceptionHandler {
    private final GlobalExceptionHandler globalExceptionHandler;

    public AccountExceptionHandler(GlobalExceptionHandler globalExceptionHandler) {
        this.globalExceptionHandler = globalExceptionHandler;
    }

    @ExceptionHandler(AccountValidationException.class)
    public ResponseEntity<ValidationErrorResponse> handleAccountValidation(AccountValidationException ex) {
        return ResponseEntity.badRequest().body(new ValidationErrorResponse(
                LocalDateTime.now(), 400, "Validation Failed", ex.getFields()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        return globalExceptionHandler.handleValidationErrors(ex);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingUser(InvalidCredentialsException ex) {
        return globalExceptionHandler.handleInvalidCredentials(ex);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> handleMalformedRequest(HttpMessageNotReadableException ex) {
        if (ex.getCause() instanceof MismatchedInputException mappingError && !mappingError.getPath().isEmpty()) {
            String field = mappingError.getPath().getFirst().getPropertyName();
            Map<String, String> messages = Map.of(
                    "dateFormat", "Date format must be MEDIUM or ISO",
                    "transactionPageSize", "Transaction page size must be 10, 25, or 50",
                    "firstName", "First name must be text",
                    "lastName", "Last name must be text",
                    "displayName", "Display name must be text",
                    "currentPassword", "Current password must be text",
                    "newPassword", "New password must be text");
            if (field != null && messages.containsKey(field)) {
                return ResponseEntity.badRequest().body(new ValidationErrorResponse(
                        LocalDateTime.now(), 400, "Validation Failed", Map.of(field, messages.get(field))));
            }
        }
        return ResponseEntity.badRequest().body(new ApiErrorResponse(
                LocalDateTime.now(), 400, "Bad Request", "Request body is missing or malformed; check field names, types, and supported values"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedError() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiErrorResponse(
                LocalDateTime.now(), 500, "Internal Server Error", "Unable to complete the account request. Please try again."));
    }
}
