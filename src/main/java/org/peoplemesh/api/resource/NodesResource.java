package org.peoplemesh.api.resource;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
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
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.model.MeshNode;
import org.peoplemesh.mcp.UserResolver;
import org.peoplemesh.repository.NodeRepository;
import org.peoplemesh.service.NodeAccessPolicyService;
import org.peoplemesh.service.NodeService;
import org.peoplemesh.service.ProfileService;
import org.peoplemesh.service.SkillAssessmentService;

import java.util.List;
import java.util.UUID;

@Path("/api/v1/nodes")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class NodesResource {

    @Inject
    UserResolver userResolver;

    @Inject
    NodeService nodeService;

    @Inject
    ProfileService profileService;

    @Inject
    NodeRepository nodeRepository;

    @Inject
    NodeAccessPolicyService nodeAccessPolicyService;

    @Inject
    SkillAssessmentService skillAssessmentService;

    @GET
    public Response listMyNodes(@QueryParam("type") String type) {
        UUID userId = userResolver.resolveUserId();
        List<NodeDto> nodes;
        if (type != null && !type.isBlank()) {
            try {
                NodeType nodeType = NodeType.valueOf(type.toUpperCase());
                nodes = nodeService.listByCreatorAndType(userId, nodeType);
            } catch (IllegalArgumentException e) {
                return Response.status(400)
                        .entity(ProblemDetail.of(400, "Bad Request", "Invalid node type: " + type))
                        .build();
            }
        } else {
            nodes = nodeService.listByCreator(userId);
        }
        return Response.ok(nodes).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createNode(@Valid NodePayload payload) {
        UUID userId = userResolver.resolveUserId();
        NodeDto created = nodeService.createNode(userId, payload);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @GET
    @Path("/{nodeId}")
    public Response getNode(@PathParam("nodeId") UUID nodeId) {
        UUID userId = userResolver.resolveUserId();
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
        UUID userId = userResolver.resolveUserId();
        MeshNode node = nodeRepository.findById(nodeId).orElse(null);
        if (node == null) {
            return Response.status(404)
                    .entity(ProblemDetail.of(404, "Not Found", "Node not found"))
                    .build();
        }
        if (!nodeAccessPolicyService.canReadNode(userId, node)) {
            return Response.status(403)
                    .entity(ProblemDetail.of(403, "Forbidden", "You do not have access to this node"))
                    .build();
        }

        List<SkillAssessmentDto> result = skillAssessmentService.listAssessments(node.id, catalogId);
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
        UUID userId = userResolver.resolveUserId();
        return nodeService.updateNode(userId, nodeId, payload)
                .map(Response::ok)
                .orElseGet(() -> Response.status(404)
                        .entity(ProblemDetail.of(404, "Not Found", "Node not found")))
                .build();
    }
}
