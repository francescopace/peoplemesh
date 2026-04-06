package org.peoplemesh.api;

import io.quarkus.security.Authenticated;
import org.peoplemesh.domain.dto.ConnectionDto;
import org.peoplemesh.domain.dto.ConnectionRequestDto;
import org.peoplemesh.mcp.UserResolver;
import org.peoplemesh.service.ConnectionService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/api/v1/connections")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class ConnectionResource {

    @Inject
    UserResolver userResolver;

    @Inject
    ConnectionService connectionService;

    @GET
    public Response getConnections() {
        UUID userId = userResolver.resolveUserId();
        List<ConnectionDto> connections = connectionService.getConnections(userId);
        return Response.ok(connections).build();
    }

    @GET
    @Path("/pending")
    public Response getPendingRequests() {
        UUID userId = userResolver.resolveUserId();
        List<ConnectionRequestDto> pending = connectionService.getPendingRequests(userId);
        return Response.ok(pending).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response requestConnection(Map<String, String> body) {
        UUID userId = userResolver.resolveUserId();
        String targetProfileId = body.get("target_profile_id");
        String message = body.get("message");

        if (targetProfileId == null || targetProfileId.isBlank()) {
            return Response.status(400)
                    .entity(ProblemDetail.of(400, "Bad Request", "target_profile_id is required"))
                    .build();
        }

        try {
            connectionService.requestConnection(userId, UUID.fromString(targetProfileId), message);
            return Response.status(Response.Status.CREATED)
                    .entity(Map.of("status", "sent"))
                    .build();
        } catch (IllegalStateException e) {
            return Response.status(429)
                    .entity(ProblemDetail.of(429, "Too Many Requests", e.getMessage()))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.status(409)
                    .entity(ProblemDetail.of(409, "Conflict", e.getMessage()))
                    .build();
        }
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response respondToConnection(@PathParam("id") UUID requestId, Map<String, Boolean> body) {
        UUID userId = userResolver.resolveUserId();
        Boolean accept = body.get("accept");
        if (accept == null) {
            return Response.status(400)
                    .entity(ProblemDetail.of(400, "Bad Request", "accept field is required"))
                    .build();
        }

        try {
            connectionService.respondToConnection(userId, requestId, accept);
            return Response.ok(Map.of("status", accept ? "accepted" : "rejected")).build();
        } catch (IllegalArgumentException e) {
            return Response.status(404)
                    .entity(ProblemDetail.of(404, "Not Found", e.getMessage()))
                    .build();
        }
    }
}
