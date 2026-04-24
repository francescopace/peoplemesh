package org.peoplemesh.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkiverse.mcp.server.TextContent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class McpToolHelperTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsePayload_nullJson_throws() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> McpToolHelper.parsePayload(null, String.class, McpToolHelper.DEFAULT_MAX_PAYLOAD_SIZE, mapper));
        assertEquals("payload is required", ex.getMessage());
    }

    @Test
    void parsePayload_blankJson_throws() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> McpToolHelper.parsePayload("   ", String.class, McpToolHelper.DEFAULT_MAX_PAYLOAD_SIZE, mapper));
        assertEquals("payload is required", ex.getMessage());
    }

    @Test
    void parsePayload_exceedsMaxSize_throws() {
        String huge = "x".repeat(100);
        var ex = assertThrows(IllegalArgumentException.class,
                () -> McpToolHelper.parsePayload(huge, String.class, 10, mapper));
        assertEquals("payload exceeds maximum size", ex.getMessage());
    }

    @Test
    void parsePayload_invalidJson_throws() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> McpToolHelper.parsePayload("{bad", Object.class, McpToolHelper.DEFAULT_MAX_PAYLOAD_SIZE, mapper));
        assertTrue(ex.getMessage().startsWith("Invalid payload format:"));
    }

    @Test
    void parsePayload_validJson_returnsDeserialized() {
        record Dto(String name) {}
        Dto result = McpToolHelper.parsePayload("{\"name\":\"test\"}", Dto.class, McpToolHelper.DEFAULT_MAX_PAYLOAD_SIZE, mapper);
        assertEquals("test", result.name());
    }

    @Test
    void parsePayload_defaultMaxSize_constant() {
        assertEquals(64 * 1024, McpToolHelper.DEFAULT_MAX_PAYLOAD_SIZE);
    }

    @Test
    void error_formatsMessage() {
        TextContent tc = McpToolHelper.error("search");
        assertEquals("Failed to search. Please try again later.", tc.text());
    }
}
