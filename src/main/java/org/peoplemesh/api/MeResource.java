package org.peoplemesh.api;

import io.quarkus.security.Authenticated;
import org.peoplemesh.domain.dto.ProfileSchema;
import org.peoplemesh.mcp.UserResolver;
import org.peoplemesh.service.GdprService;
import org.peoplemesh.service.ProfileService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.UUID;

@Path("/api/v1/me")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class MeResource {

    @Inject
    UserResolver userResolver;

    @Inject
    ProfileService profileService;

    @Inject
    GdprService gdprService;

    @GET
    public Response getProfile() {
        UUID userId = userResolver.resolveUserId();
        return profileService.getProfile(userId)
                .map(schema -> Response.ok(schema).build())
                .orElse(Response.status(404)
                        .entity(ProblemDetail.of(404, "Not Found", "No profile exists"))
                        .build());
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateProfile(ProfileSchema updates) {
        UUID userId = userResolver.resolveUserId();
        return profileService.updateProfile(userId, updates)
                .map(p -> Response.ok(profileService.getProfile(userId).orElse(null)).build())
                .orElse(Response.status(404)
                        .entity(ProblemDetail.of(404, "Not Found", "No profile to update"))
                        .build());
    }

    @DELETE
    public Response deleteAccount() {
        UUID userId = userResolver.resolveUserId();
        gdprService.deleteAllData(userId);
        return Response.noContent().build();
    }

    @GET
    @Path("/data")
    @Produces(MediaType.APPLICATION_JSON)
    public Response exportData() {
        UUID userId = userResolver.resolveUserId();
        String json = gdprService.exportAllData(userId);
        return Response.ok(json)
                .header("Content-Disposition", "attachment; filename=\"peoplemesh-data-export.json\"")
                .build();
    }

    @PATCH
    @Path("/restrict")
    public Response restrictProcessing() {
        UUID userId = userResolver.resolveUserId();
        gdprService.restrictProcessing(userId);
        return Response.noContent().build();
    }
}
