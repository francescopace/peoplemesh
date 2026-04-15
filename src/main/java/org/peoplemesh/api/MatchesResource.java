package org.peoplemesh.api;

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
import org.peoplemesh.domain.model.MeshNode;
import org.peoplemesh.mcp.UserResolver;
import org.peoplemesh.service.EmbeddingService;
import org.peoplemesh.service.EmbeddingTextBuilder;
import org.peoplemesh.service.MatchingService;
import org.peoplemesh.service.SearchService;

import java.util.List;
import java.util.UUID;

@Path("/api/v1/matches")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class MatchesResource {

    @Inject
    UserResolver userResolver;

    @Inject
    MatchingService matchingService;

    @Inject
    EmbeddingService embeddingService;

    @Inject
    SearchService searchService;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response matchFromSchema(
            @Valid ProfileSchema profile,
            @QueryParam("type") String type,
            @QueryParam("country") String country) {
        UUID userId = userResolver.resolveUserId();
        String text = EmbeddingTextBuilder.buildFromSchema(profile);
        float[] embedding = embeddingService.generateEmbedding(text);
        if (embedding == null) {
            return Response.status(400)
                    .entity(ProblemDetail.of(400, "Bad Request", "Embedding input was empty"))
                    .build();
        }
        List<MeshMatchResult> matches = matchingService.findAllMatches(userId, embedding, type, country);
        return Response.ok(matches).build();
    }

    @POST
    @Path("prompt")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response matchFromPrompt(@Valid SearchRequest request) {
        UUID userId = userResolver.resolveUserId();
        SearchResponse result = searchService.search(userId, request.query(), request.country());
        return Response.ok(result).build();
    }

    @GET
    @Path("me")
    public Response matchMyProfile(
            @QueryParam("type") String type,
            @QueryParam("country") String country) {
        UUID userId = userResolver.resolveUserId();
        List<MeshMatchResult> matches = matchingService.findAllMatches(userId, type, country);
        return Response.ok(matches).build();
    }

    @GET
    @Path("{nodeId}")
    public Response matchFromNode(
            @PathParam("nodeId") UUID nodeId,
            @QueryParam("type") String type,
            @QueryParam("country") String country) {
        UUID userId = userResolver.resolveUserId();
        MeshNode node = MeshNode.<MeshNode>findByIdOptional(nodeId).orElse(null);
        if (node == null || node.embedding == null) {
            return Response.status(404)
                    .entity(ProblemDetail.of(404, "Not Found", "Node not found or has no embedding"))
                    .build();
        }
        boolean isOwner = userId.equals(node.createdBy);
        boolean isPublicNonUser = node.searchable && node.nodeType != org.peoplemesh.domain.enums.NodeType.USER;
        if (!isOwner && !isPublicNonUser) {
            return Response.status(403)
                    .entity(ProblemDetail.of(403, "Forbidden", "You do not have access to this node"))
                    .build();
        }
        List<MeshMatchResult> matches = matchingService.findAllMatches(
                userId, node.embedding, type, country);
        return Response.ok(matches).build();
    }
}
