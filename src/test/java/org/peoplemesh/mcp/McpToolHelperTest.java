package org.peoplemesh.mcp;

import io.quarkiverse.mcp.server.TextContent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class McpToolHelperTest {

    @Test
    void error_formatsMessage() {
        TextContent tc = McpToolHelper.error("search");
        assertEquals("Failed to search. Please try again later.", tc.text());
    }
}
