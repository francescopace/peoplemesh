package org.peoplemesh.mcp;

import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkus.security.Authenticated;
import org.peoplemesh.domain.dto.ConnectionDto;
import org.peoplemesh.domain.dto.ConnectionRequestDto;
import org.peoplemesh.service.ConnectionService;
import jakarta.inject.Inject;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.UUID;

public class ConnectionTools {

    private static final Logger LOG = Logger.getLogger(ConnectionTools.class);

    @Inject
    UserResolver userResolver;

    @Inject
    ConnectionService connectionService;

    @Inject
    ObjectMapper objectMapper;

    @Tool(name = "peoplemesh_request_connection",
          description = "Send a connection request to a matched professional. Provide the target profile ID and an optional message (max 300 chars).")
    @Authenticated
    public TextContent requestConnection(String targetProfileId, String message) {
        try {
            UUID userId = userResolver.resolveUserId();
            UUID targetId = UUID.fromString(targetProfileId);
            connectionService.requestConnection(userId, targetId, message);
            return new TextContent("Connection request sent. You'll be notified when they respond.");
        } catch (SecurityException e) {
            return new TextContent("Error: " + e.getMessage());
        } catch (IllegalStateException e) {
            return new TextContent("Rate limit: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return new TextContent("Error: " + e.getMessage());
        } catch (Exception e) {
            LOG.error("Failed to send connection request", e);
            return new TextContent("Failed to send connection request. Please try again later.");
        }
    }

    @Tool(name = "peoplemesh_respond_to_connection",
          description = "Accept or reject a pending connection request. Provide the request ID and true/false.")
    @Authenticated
    public TextContent respondToConnection(String requestId, boolean accept) {
        try {
            UUID userId = userResolver.resolveUserId();
            connectionService.respondToConnection(userId, UUID.fromString(requestId), accept);
            return new TextContent(accept
                    ? "Connection accepted! You can now exchange contact information."
                    : "Connection request declined.");
        } catch (SecurityException e) {
            return new TextContent("Error: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return new TextContent("Error: " + e.getMessage());
        } catch (IllegalStateException e) {
            return new TextContent("Error: " + e.getMessage());
        } catch (Exception e) {
            LOG.error("Failed to respond to connection", e);
            return new TextContent("Failed to respond to connection request. Please try again later.");
        }
    }

    @Tool(name = "peoplemesh_get_connections",
          description = "List your active connections and pending connection requests.")
    @Authenticated
    public TextContent getConnections() {
        try {
            UUID userId = userResolver.resolveUserId();
            List<ConnectionDto> connections = connectionService.getConnections(userId);
            List<ConnectionRequestDto> pending = connectionService.getPendingRequests(userId);

            StringBuilder sb = new StringBuilder();
            sb.append("Active connections: ").append(connections.size()).append("\n");
            if (!connections.isEmpty()) {
                sb.append(objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(connections));
            }
            sb.append("\n\nPending requests: ").append(pending.size()).append("\n");
            if (!pending.isEmpty()) {
                sb.append(objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(pending));
            }
            return new TextContent(sb.toString());
        } catch (SecurityException e) {
            return new TextContent("Error: " + e.getMessage());
        } catch (Exception e) {
            LOG.error("Error retrieving connections", e);
            return new TextContent("Error retrieving connections. Please try again later.");
        }
    }
}
