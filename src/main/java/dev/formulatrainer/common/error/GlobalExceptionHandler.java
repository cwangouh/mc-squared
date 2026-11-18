package dev.formulatrainer.common.error;

import dev.formulatrainer.common.api.ApiErrorResponse;
import dev.formulatrainer.common.api.FieldErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
        MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<FieldErrorResponse> fieldErrors = exception.getBindingResult()
            .getFieldErrors()
            .stream()
            .sorted(
                Comparator.comparing(FieldError::getField))
            .map(
                error -> new FieldErrorResponse(error.getField(), error.getDefaultMessage()))
            .toList();

        String fields = fieldErrors.stream()
            .map(FieldErrorResponse::field)
            .distinct()
            .sorted()
            .toList()
            .toString();
        logClientError(request, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", exception, "fields=" + fields);
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
        ConstraintViolationException exception, HttpServletRequest request) {
        List<FieldErrorResponse> fieldErrors = exception.getConstraintViolations()
            .stream()
            .map(
                violation -> new FieldErrorResponse(
                    violation.getPropertyPath()
                        .toString(),
                    violation.getMessage()))
            .sorted(
                Comparator.comparing(FieldErrorResponse::field))
            .toList();

        String fields = fieldErrors.stream()
            .map(FieldErrorResponse::field)
            .distinct()
            .sorted()
            .toList()
            .toString();
        logClientError(request, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", exception, "fields=" + fields);
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", fieldErrors);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
        MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
        String message = "Invalid value for parameter '%s'".formatted(exception.getName());
        logClientError(request, HttpStatus.BAD_REQUEST, "INVALID_REQUEST", exception, message);
        return build(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleBadRequest(
        IllegalArgumentException exception, HttpServletRequest request) {
        logClientError(request, HttpStatus.BAD_REQUEST, "INVALID_REQUEST", exception, exception.getMessage());
        return build(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", exception.getMessage());
    }

    @ExceptionHandler(PayloadTooLargeException.class)
    public ResponseEntity<ApiErrorResponse> handlePayloadTooLarge(
        PayloadTooLargeException exception, HttpServletRequest request) {
        logClientError(request, HttpStatus.CONTENT_TOO_LARGE, "PAYLOAD_TOO_LARGE", exception, exception.getMessage());
        return build(HttpStatus.CONTENT_TOO_LARGE, "PAYLOAD_TOO_LARGE", exception.getMessage());
    }

    @ExceptionHandler(UnsupportedMediaFileException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedMediaFile(
        UnsupportedMediaFileException exception, HttpServletRequest request) {
        logClientError(
            request, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", exception, exception.getMessage());
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", exception.getMessage());
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiErrorResponse> handleNotFound(Exception exception, HttpServletRequest request) {
        logClientError(request, HttpStatus.NOT_FOUND, "NOT_FOUND", exception, "Resource not found");
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found");
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceNotFound(
        ResourceNotFoundException exception, HttpServletRequest request) {
        logClientError(request, HttpStatus.NOT_FOUND, "NOT_FOUND", exception, exception.getMessage());
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(
        HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
        logClientError(
            request, HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", exception, "HTTP method is not supported");
        return build(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "HTTP method is not supported");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        LOGGER.error(
            "Unexpected server error: method={}, path={}", request.getMethod(), request.getRequestURI(), exception);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected server error");
    }

    private static void logClientError(
        HttpServletRequest request, HttpStatus status, String code, Exception exception, String reason) {
        LOGGER.warn(
            "Handled request error: method={}, path={}, status={}, code={}, exception={}, reason={}",
            request.getMethod(),
            request.getRequestURI(),
            status.value(),
            code,
            exception.getClass()
                .getSimpleName(),
            reason);
    }

    private static ResponseEntity<ApiErrorResponse> build(HttpStatus status, String code, String message) {
        return build(status, code, message, List.of());
    }

    private static ResponseEntity<ApiErrorResponse> build(
        HttpStatus status, String code, String message, List<FieldErrorResponse> fieldErrors) {
        return ResponseEntity.status(status)
            .body(new ApiErrorResponse(status.value(), code, message, fieldErrors));
    }

}
