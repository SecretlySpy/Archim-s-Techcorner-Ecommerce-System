package com.archimstechcorner.marketplace.shared.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.UUID;

/**
 * Translates every exception into the standard error envelope. No stack traces, provider payloads, or
 * sensitive data ever reach the client; specifics are logged against the returned {@code correlationId}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Typed application errors carry their own code and a client-safe message. */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError.Envelope> handleApi(ApiException ex) {
        String correlationId = UUID.randomUUID().toString();
        log.info("api_error code={} correlationId={} message={}", ex.errorCode(), correlationId, ex.getMessage());
        return respond(ex.errorCode(), ex.getMessage(), correlationId, ex.details());
    }

    /** Bean Validation failures become a 400 with per-field detail (no submitted secret is echoed). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError.Envelope> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::formatFieldError)
                .toList();
        return respond(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid.",
                UUID.randomUUID().toString(), details);
    }

    /** Authenticated-but-forbidden (failed @PreAuthorize / method security). */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError.Envelope> handleAccessDenied(AccessDeniedException ex) {
        return respond(ErrorCode.FORBIDDEN, "You do not have permission to perform this action.",
                UUID.randomUUID().toString(), List.of());
    }

    /** Anything unexpected is logged in full server-side and returned as an opaque 500. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError.Envelope> handleUnexpected(Exception ex) {
        String correlationId = UUID.randomUUID().toString();
        log.error("unexpected_error correlationId={}", correlationId, ex);
        return respond(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred.", correlationId, List.of());
    }

    private static String formatFieldError(FieldError fieldError) {
        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
    }

    private static ResponseEntity<ApiError.Envelope> respond(
            ErrorCode code, String message, String correlationId, List<String> details) {
        ApiError error = new ApiError(code.name(), message, correlationId, details);
        return ResponseEntity.status(code.status()).body(error.wrap());
    }
}
