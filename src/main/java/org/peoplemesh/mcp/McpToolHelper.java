package org.peoplemesh.mcp;

import io.quarkiverse.mcp.server.TextContent;

/**
 * Shared utilities for MCP Tool implementations.
 */
public final class McpToolHelper {

    private McpToolHelper() {}

    public static TextContent error(String action) {
        return new TextContent("Failed to " + action + ". Please try again later.");
    }
}
