package com.archimstechcorner.marketplace.shared.error;

import java.util.List;

/**
 * Domain/application exception carrying a typed {@link ErrorCode} and a client-safe message.
 * The {@link GlobalExceptionHandler} translates it into the standard error envelope.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final List<String> details;

    public ApiException(ErrorCode errorCode, String message) {
        this(errorCode, message, List.of());
    }

    public ApiException(ErrorCode errorCode, String message, List<String> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details == null ? List.of() : List.copyOf(details);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public List<String> details() {
        return details;
    }
}
