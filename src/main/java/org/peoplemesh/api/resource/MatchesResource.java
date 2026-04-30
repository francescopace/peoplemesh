package org.peoplemesh.api.resource;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
import org.peoplemesh.domain.dto.SearchQuery;
import org.peoplemesh.domain.dto.SearchRequest;
import org.peoplemesh.domain.dto.SearchResponse;
import org.peoplemesh.service.CurrentUserService;
import org.peoplemesh.service.MatchesService;

import java.util.List;
import java.util.UUID;

@Path("/api/v1/matches")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class MatchesResource {

    @Inject
    CurrentUserService currentUserService;

    @Inject
    MatchesService matchesService;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response matchFromSchema(
            @NotNull @Valid SearchQuery parsedQuery,
            @QueryParam("type") @Size(max = 40) @Pattern(regexp = "^[A-Za-z_]*$") String type,
            @QueryParam("country") @Pattern(regexp = "^[A-Za-z]{2}$|^$") String country,
            @QueryParam("limit") @Min(1) @Max(100) Integer limit,
            @QueryParam("offset") @Min(0) Integer offset) {
        UUID userId = currentUserService.resolveUserId();
        List<MeshMatchResult> matches = matchesService.matchFromSchema(
                userId, parsedQuery, type, country, limit, offset);
        return Response.ok(matches).build();
    }

    @POST
    @Path("prompt")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response matchFromPrompt(
            @Valid SearchRequest request,
            @QueryParam("limit") @Min(1) @Max(100) Integer limit) {
        UUID userId = currentUserService.resolveUserId();
        SearchResponse result = matchesService.matchFromPrompt(userId, request, limit);
        return Response.ok(result).build();
    }

    @GET
    @Path("me")
    public Response matchMyProfile(
            @QueryParam("type") @Size(max = 40) @Pattern(regexp = "^[A-Za-z_]*$") String type,
            @QueryParam("country") @Pattern(regexp = "^[A-Za-z]{2}$|^$") String country,
            @QueryParam("limit") @Min(1) @Max(100) Integer limit,
            @QueryParam("offset") @Min(0) Integer offset) {
        UUID userId = currentUserService.resolveUserId();
        List<MeshMatchResult> matches = matchesService.matchMyProfile(userId, type, country, limit, offset);
        return Response.ok(matches).build();
    }

    @GET
    @Path("{nodeId}")
    public Response matchFromNode(
            @PathParam("nodeId") UUID nodeId,
            @QueryParam("type") @Size(max = 40) @Pattern(regexp = "^[A-Za-z_]*$") String type,
            @QueryParam("country") @Pattern(regexp = "^[A-Za-z]{2}$|^$") String country) {
        UUID userId = currentUserService.resolveUserId();
        List<MeshMatchResult> matches = matchesService.matchFromNode(userId, nodeId, type, country);
        return Response.ok(matches).build();
    }
}
