package dev.portfolio.finance.exception.category;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import tools.jackson.databind.exc.MismatchedInputException;
import dev.portfolio.finance.controller.CategoryController;
import dev.portfolio.finance.dto.error.ApiErrorResponse;
import dev.portfolio.finance.dto.error.ValidationErrorResponse;
import dev.portfolio.finance.exception.GlobalExceptionHandler;

/**
 * Category API error contract. Business errors carry a stable {@code code}; input errors
 * use the standard field-validation response. Nothing here echoes SQL, constraint names,
 * request values, or whether another user's category exists.
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RestControllerAdvice(assignableTypes = CategoryController.class)
public class CategoryExceptionHandler {

    static final String INVALID_ID_MESSAGE = "Category ID must be a positive whole number";
    static final String INVALID_MONTH_MESSAGE = "Month must be a whole number between 1 and 12";
    static final String INVALID_YEAR_MESSAGE = "Year must be a whole number";

    private static final Map<String, String> TYPE_MESSAGES = Map.of(
            "name", "Category name must be text",
            "budgetEnabled", "Budget enabled must be true or false",
            "iconKey", "Icon must be text");

    private final GlobalExceptionHandler globalExceptionHandler;

    public CategoryExceptionHandler(GlobalExceptionHandler globalExceptionHandler) {
        this.globalExceptionHandler = globalExceptionHandler;
    }

    @ExceptionHandler(CategoryNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(CategoryNotFoundException ex) {
        return globalExceptionHandler.handleCategoryNotFound(ex);
    }

    @ExceptionHandler(DuplicateCategoryException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicate(DuplicateCategoryException ex) {
        return globalExceptionHandler.handleDuplicateCategory(ex);
    }

    @ExceptionHandler(CategoryBuiltInException.class)
    public ResponseEntity<ApiErrorResponse> handleBuiltIn(CategoryBuiltInException ex) {
        return error(HttpStatus.FORBIDDEN, ex.getMessage(), "CATEGORY_BUILT_IN");
    }

    @ExceptionHandler(CategoryInUseException.class)
    public ResponseEntity<ApiErrorResponse> handleInUse(CategoryInUseException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), "CATEGORY_IN_USE");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        return globalExceptionHandler.handleValidationErrors(ex);
    }

    @ExceptionHandler(InvalidCategoryNameException.class)
    public ResponseEntity<ValidationErrorResponse> handleInvalidName(InvalidCategoryNameException ex) {
        return validation(Map.of("name", ex.getMessage()));
    }

    @ExceptionHandler(CategoryValidationException.class)
    public ResponseEntity<ValidationErrorResponse> handleCategoryValidation(CategoryValidationException ex) {
        return globalExceptionHandler.handleCategoryValidation(ex);
    }

    /**
     * Method validation: with {@code @Positive} on the path ID, Spring also reports
     * {@code @Valid} body errors here, so body field errors keep their field names and a
     * rejected path ID becomes {@code id}.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ValidationErrorResponse> handleMethodValidation(HandlerMethodValidationException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            if (result instanceof ParameterErrors bodyErrors) {
                bodyErrors.getFieldErrors().forEach(error ->
                        fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
            } else {
                fields.put("id", INVALID_ID_MESSAGE);
            }
        }
        return validation(fields);
    }

    /**
     * A value that cannot be converted, reported under its own parameter name: the summary's
     * {@code month} or {@code year}, otherwise the path {@code id}.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ValidationErrorResponse> handleUnparseableValue(MethodArgumentTypeMismatchException ex) {
        String message = switch (ex.getName()) {
            case "month" -> INVALID_MONTH_MESSAGE;
            case "year" -> INVALID_YEAR_MESSAGE;
            default -> null;
        };
        return message == null
                ? validation(Map.of("id", INVALID_ID_MESSAGE))
                : validation(Map.of(ex.getName(), message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> handleMalformedRequest(HttpMessageNotReadableException ex) {
        if (ex.getCause() instanceof MismatchedInputException mappingError && !mappingError.getPath().isEmpty()) {
            String field = mappingError.getPath().getFirst().getPropertyName();
            if (field != null && TYPE_MESSAGES.containsKey(field)) {
                return validation(Map.of(field, TYPE_MESSAGES.get(field)));
            }
        }
        return error(HttpStatus.BAD_REQUEST,
                "Request body is missing or malformed; check field names and types", null);
    }

    /**
     * Keeps Spring's own client-error statuses (for example 415 for a non-JSON body);
     * anything else, including database errors that are not a mapped duplicate or in-use
     * conflict, becomes a generic 500 with no detail.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedError(Exception ex) {
        if (ex instanceof ErrorResponse framework && framework.getStatusCode().is4xxClientError()) {
            HttpStatus status = HttpStatus.valueOf(framework.getStatusCode().value());
            return error(status, "The request could not be processed", null);
        }
        return error(HttpStatus.INTERNAL_SERVER_ERROR,
                "Unable to complete the category request. Please try again.", null);
    }

    private static ResponseEntity<ApiErrorResponse> error(HttpStatus status, String message, String code) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                LocalDateTime.now(), status.value(), status.getReasonPhrase(), message, code));
    }

    private static ResponseEntity<ValidationErrorResponse> validation(Map<String, String> fields) {
        return ResponseEntity.badRequest().body(new ValidationErrorResponse(
                LocalDateTime.now(), HttpStatus.BAD_REQUEST.value(), "Validation Failed", fields));
    }
}
