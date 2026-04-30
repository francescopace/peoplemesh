package org.peoplemesh.api.resource;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.domain.dto.MaintenanceTuningMatchesRequest;
import org.peoplemesh.domain.dto.MeshMatchResult;
import org.peoplemesh.domain.dto.MaintenanceNodeCandidateDto;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.exception.NotFoundBusinessException;
import org.peoplemesh.domain.exception.ValidationBusinessException;
import org.peoplemesh.security.MaintenanceAccessGuard;
import org.peoplemesh.service.MaintenanceService;
import org.peoplemesh.service.MatchesService;
import org.peoplemesh.service.NodeService;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Path("/api/v1/maintenance")
@Produces(MediaType.APPLICATION_JSON)
public class MaintenanceResource {

    @Inject
    AppConfig config;

    @Inject
    MaintenanceService maintenanceService;

    @Inject
    NodeService nodeService;

    @Inject
    MatchesService matchesService;

    @Context
    HttpHeaders httpHeaders;

    @POST
    @Path("/purge-consent-tokens")
    public Response purgeConsentTokens(@HeaderParam("X-Maintenance-Key") @Size(max = 256) String key) {
        assertAuthorized(key);
        return Response.ok(maintenanceService.purgeConsentTokens()).build();
    }

    @POST
    @Path("/enforce-retention")
    public Response enforceRetention(@HeaderParam("X-Maintenance-Key") @Size(max = 256) String key) {
        assertAuthorized(key);
        return Response.ok(maintenanceService.enforceRetention()).build();
    }

    @POST
    @Path("/run-clustering")
    public Response runClustering(@HeaderParam("X-Maintenance-Key") @Size(max = 256) String key) {
        assertAuthorized(key);
        return Response.ok(maintenanceService.runClustering()).build();
    }

    @POST
    @Path("/regenerate-embeddings")
    public Response regenerateEmbeddings(@HeaderParam("X-Maintenance-Key") @Size(max = 256) String key,
                                         @QueryParam("nodeType")
                                         @Pattern(regexp = "^[A-Za-z_]*$", message = "nodeType must be alphabetic")
                                         String nodeTypeParam,
                                         @QueryParam("onlyMissing") @DefaultValue("true") boolean onlyMissing,
                                         @QueryParam("batchSize") @DefaultValue("1") @Min(1) @Max(1000) int batchSize) {
        assertAuthorized(key);
        return Response.accepted(maintenanceService.startEmbeddingRegeneration(
                nodeTypeParam, onlyMissing, batchSize
        )).build();
    }

    @GET
    @Path("/nodes")
    public Response listNodes(@HeaderParam("X-Maintenance-Key") @Size(max = 256) String key,
                              @QueryParam("type")
                              @Pattern(regexp = "^[A-Za-z_]*$", message = "type must be alphabetic")
                              String typeParam,
                              @QueryParam("searchable") @DefaultValue("true") boolean searchable,
                              @QueryParam("page") @DefaultValue("0") @Min(0) int page,
                              @QueryParam("size") @DefaultValue("100") @Min(1) @Max(500) int size) {
        assertAuthorized(key);
        NodeType type = parseNodeType(typeParam);
        if (typeParam != null && !typeParam.isBlank() && type == null) {
            throw new ValidationBusinessException("Invalid node type");
        }
        var rows = nodeService.listNodes(type, searchable, page, size, true, false).stream()
                .map(n -> new MaintenanceNodeCandidateDto(
                        n.id(),
                        n.nodeType(),
                        n.title(),
                        n.description(),
                        n.tags(),
                        n.country(),
                        n.structuredData()
                ))
                .toList();
        return Response.ok(rows).build();
    }

    @GET
    @Path("/regenerate-embeddings/{jobId}")
    public Response regenerateEmbeddingsStatus(@HeaderParam("X-Maintenance-Key") @Size(max = 256) String key,
                                               @PathParam("jobId")
                                               @Pattern(regexp = "^[0-9a-fA-F\\-]{36}$", message = "jobId must be a UUID")
                                               String jobIdParam) {
        assertAuthorized(key);
        var status = maintenanceService.getEmbeddingRegenerationStatus(jobIdParam)
                .orElseThrow(() -> new NotFoundBusinessException("Embedding job not found"));
        return Response.ok(status).build();
    }

    @POST
    @Path("/tuning/matches/{userId}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response tuneMatchMyProfile(
            @HeaderParam("X-Maintenance-Key") @Size(max = 256) String key,
            @PathParam("userId") @Pattern(regexp = "^[0-9a-fA-F\\-]{36}$", message = "userId must be a UUID") String userIdParam,
            @NotNull @Valid MaintenanceTuningMatchesRequest request) {
        assertAuthorized(key);
        UUID userId;
        try {
            userId = UUID.fromString(userIdParam.trim());
        } catch (IllegalArgumentException ex) {
            throw new ValidationBusinessException("Invalid userId format");
        }
        List<MeshMatchResult> matches = matchesService.matchMyProfile(
                userId,
                request.type(),
                request.country(),
                request.limit(),
                request.offset(),
                request.searchOptions());
        return Response.ok(matches).build();
    }

    private void assertAuthorized(String key) {
        MaintenanceAccessGuard.assertAuthorized(key, config, httpHeaders);
    }

    private NodeType parseNodeType(String typeParam) {
        if (typeParam == null || typeParam.isBlank()) {
            return null;
        }
        try {
            return NodeType.valueOf(typeParam.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
