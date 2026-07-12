package com.archimstechcorner.marketplace.shared.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Writes the standard error envelope directly to the servlet response. Used by the security filters
 * and handlers that run outside {@code @RestControllerAdvice} (entry point, access-denied, origin check).
 */
@Component
public class JsonErrorWriter {

    private final ObjectMapper objectMapper;

    public JsonErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, ErrorCode code, String message) throws IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // A correlation id lets support tie a client-visible failure to server logs without leaking detail.
        ApiError error = new ApiError(code.name(), message, UUID.randomUUID().toString(), List.of());
        objectMapper.writeValue(response.getOutputStream(), error.wrap());
    }
}
