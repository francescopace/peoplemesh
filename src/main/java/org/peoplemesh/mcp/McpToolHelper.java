package org.peoplemesh.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkiverse.mcp.server.TextContent;

/**
 * Shared utilities for MCP Tool implementations: payload parsing, size validation, error messages.
 */
public final class McpToolHelper {

    public static final int DEFAULT_MAX_PAYLOAD_SIZE = 64 * 1024;

    private McpToolHelper() {}

    public static <T> T parsePayload(String json, Class<T> type, int maxSize, ObjectMapper mapper) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("payload is required");
        }
        if (json.length() > maxSize) {
            throw new IllegalArgumentException("payload exceeds maximum size");
        }
        try {
            return mapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid payload format: " + e.getMessage());
        }
    }

    public static TextContent error(String action) {
        return new TextContent("Failed to " + action + ". Please try again later.");
    }
}
