package org.peoplemesh.api;

import io.quarkus.security.Authenticated;
import org.peoplemesh.domain.dto.PrivacyDashboard;
import org.peoplemesh.mcp.UserResolver;
import org.peoplemesh.service.GdprService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.UUID;

@Path("/api/v1/privacy")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class PrivacyResource {

    @Inject
    UserResolver userResolver;

    @Inject
    GdprService gdprService;

    @GET
    @Path("/activity")
    public Response getPrivacyDashboard() {
        UUID userId = userResolver.resolveUserId();
        PrivacyDashboard dashboard = gdprService.getPrivacyDashboard(userId);
        return Response.ok(dashboard).build();
    }
}
