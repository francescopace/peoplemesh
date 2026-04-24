package org.peoplemesh.api.resource;

import io.quarkus.security.Authenticated;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.peoplemesh.api.error.ProblemDetail;
import org.peoplemesh.service.NodeService;
import org.peoplemesh.service.ProfileService;

import java.util.UUID;

@Path("/api/v1/nodes")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
@Blocking
public class NodesResource {

    @Inject
    NodeService nodeService;

    @Inject
    ProfileService profileService;

    @GET
    @Path("/{nodeId}")
    public Response getNode(@PathParam("nodeId") UUID nodeId,
                            @QueryParam("includeEmbedding") @DefaultValue("false") boolean includeEmbedding) {
        return nodeService.getNode(nodeId, includeEmbedding)
                .map(Response::ok)
                .orElseGet(() -> Response.status(404)
                        .entity(ProblemDetail.of(404, "Not Found", "Node not found")))
                .build();
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

}
