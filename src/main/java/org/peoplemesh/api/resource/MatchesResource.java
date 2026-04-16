package org.peoplemesh.api.resource;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.peoplemesh.domain.dto.MeshMatchResult;
import org.peoplemesh.domain.dto.ProfileSchema;
import org.peoplemesh.domain.dto.SearchRequest;
import org.peoplemesh.domain.dto.SearchResponse;
import org.peoplemesh.mcp.UserResolver;
import org.peoplemesh.service.MatchesService;

import java.util.List;
import java.util.UUID;

@Path("/api/v1/matches")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class MatchesResource {

    @Inject
    UserResolver userResolver;

    @Inject
    MatchesService matchesService;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response matchFromSchema(
            @Valid ProfileSchema profile,
            @QueryParam("type") String type,
            @QueryParam("country") String country) {
        UUID userId = userResolver.resolveUserId();
        List<MeshMatchResult> matches = matchesService.matchFromSchema(userId, profile, type, country);
        return Response.ok(matches).build();
    }

    @POST
    @Path("prompt")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response matchFromPrompt(@Valid SearchRequest request) {
        UUID userId = userResolver.resolveUserId();
        SearchResponse result = matchesService.matchFromPrompt(userId, request);
        return Response.ok(result).build();
    }

    @GET
    @Path("me")
    public Response matchMyProfile(
            @QueryParam("type") String type,
            @QueryParam("country") String country) {
        UUID userId = userResolver.resolveUserId();
        List<MeshMatchResult> matches = matchesService.matchMyProfile(userId, type, country);
        return Response.ok(matches).build();
    }

    @GET
    @Path("{nodeId}")
    public Response matchFromNode(
            @PathParam("nodeId") UUID nodeId,
            @QueryParam("type") String type,
            @QueryParam("country") String country) {
        UUID userId = userResolver.resolveUserId();
        List<MeshMatchResult> matches = matchesService.matchFromNode(userId, nodeId, type, country);
        return Response.ok(matches).build();
    }
}
