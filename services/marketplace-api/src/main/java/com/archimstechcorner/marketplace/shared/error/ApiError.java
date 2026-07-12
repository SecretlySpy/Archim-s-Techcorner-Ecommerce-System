package com.archimstechcorner.marketplace.shared.error;

import java.util.List;

/** Body of the standard error envelope: {@code { "error": { code, message, correlationId, details } } }. */
public record ApiError(String code, String message, String correlationId, List<String> details) {

    public record Envelope(ApiError error) {
    }

    /** Wraps this error in its transport envelope. */
    public Envelope wrap() {
        return new Envelope(this);
    }
}
