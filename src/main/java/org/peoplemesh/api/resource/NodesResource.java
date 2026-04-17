package org.peoplemesh.api.resource;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.peoplemesh.api.error.ProblemDetail;
import org.peoplemesh.domain.dto.NodeDto;
import org.peoplemesh.domain.dto.NodePayload;
import org.peoplemesh.domain.dto.SkillAssessmentDto;
import org.peoplemesh.service.CurrentUserService;
import org.peoplemesh.service.NodeService;
import org.peoplemesh.service.ProfileService;

import java.util.List;
import java.util.UUID;

@Path("/api/v1/nodes")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class NodesResource {

    @Inject
    CurrentUserService currentUserService;

    @Inject
    NodeService nodeService;

    @Inject
    ProfileService profileService;

    @GET
    public Response listMyNodes(
            @QueryParam("type")
            @Pattern(regexp = "^[A-Za-z_]*$", message = "type must be alphabetic")
            String type
    ) {
        UUID userId = currentUserService.resolveUserId();
        List<NodeDto> nodes = nodeService.listByCreatorFiltered(userId, type);
        return Response.ok(nodes).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createNode(@Valid NodePayload payload) {
        UUID userId = currentUserService.resolveUserId();
        NodeDto created = nodeService.createNode(userId, payload);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @GET
    @Path("/{nodeId}")
    public Response getNode(@PathParam("nodeId") UUID nodeId) {
        UUID userId = currentUserService.resolveUserId();
        return nodeService.getNode(nodeId)
                .or(() -> nodeService.getNodeForCreator(nodeId, userId))
                .map(Response::ok)
                .orElseGet(() -> Response.status(404)
                        .entity(ProblemDetail.of(404, "Not Found", "Node not found")))
                .build();
    }

    @GET
    @Path("/{nodeId}/skills")
    public Response getNodeSkills(@PathParam("nodeId") UUID nodeId,
                                  @QueryParam("catalog_id") UUID catalogId) {
        UUID userId = currentUserService.resolveUserId();
        List<SkillAssessmentDto> result = nodeService.getNodeSkillsForUser(userId, nodeId, catalogId);
        return Response.ok(result).build();
    }

    @GET
    @Path("/{nodeId}/profile")
    public Response getNodeProfile(@PathParam("nodeId") UUID nodeId) {
        return profileService.getPublicProfile(nodeId)
                .map(schema -> Response.ok(schema).build())
                .orElse(Response.status(404)
                        .entity(ProblemDetail.of(404, "Not Found", "Profile not found or not published"))
                        .build());
    }

    @PUT
    @Path("/{nodeId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateNode(@PathParam("nodeId") UUID nodeId, @Valid NodePayload payload) {
        UUID userId = currentUserService.resolveUserId();
        return nodeService.updateNode(userId, nodeId, payload)
                .map(Response::ok)
                .orElseGet(() -> Response.status(404)
                        .entity(ProblemDetail.of(404, "Not Found", "Node not found")))
                .build();
    }
}
