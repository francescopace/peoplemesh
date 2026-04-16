package org.peoplemesh.api.resource;

import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
import org.jboss.logging.Logger;
import org.peoplemesh.api.MaintenanceAuthHelper;
import org.peoplemesh.api.error.ProblemDetail;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.service.ClusteringScheduler;
import org.peoplemesh.service.GdprService;
import org.peoplemesh.service.JdbcConsentTokenStore;
import org.peoplemesh.service.LdapImportService;
import org.peoplemesh.service.MaintenanceService;
import org.peoplemesh.service.NodeEmbeddingMaintenanceService;

import java.util.Map;
import java.util.UUID;

/**
 * Maintenance endpoint for scheduled tasks.
 * Protected by a shared secret (API key) and optional IP allowlist so it can be called by
 * external schedulers (AWS EventBridge, cron, Lambda) without a user session.
 */
@Path("/api/v1/maintenance")
@Produces(MediaType.APPLICATION_JSON)
@Blocking
public class MaintenanceResource {

    private static final Logger LOG = Logger.getLogger(MaintenanceResource.class);

    @Inject
    AppConfig config;

    @Inject
    JdbcConsentTokenStore consentTokenStore;

    @Inject
    GdprService gdprService;

    @Inject
    ClusteringScheduler clusteringScheduler;

    @Inject
    MaintenanceService maintenanceService;

    @Inject
    LdapImportService ldapImportService;

    @Inject
    NodeEmbeddingMaintenanceService nodeEmbeddingMaintenanceService;

    @Context
    HttpHeaders httpHeaders;

    @POST
    @Path("/purge-consent-tokens")
    public Response purgeConsentTokens(@HeaderParam("X-Maintenance-Key") String key) {
        assertAuthorized(key);
        int purged = consentTokenStore.purgeExpired();
        LOG.infof("Maintenance: purged %d expired consent tokens", purged);
        return Response.ok(Map.of("action", "purge-consent-tokens", "purged", purged)).build();
    }

    @POST
    @Path("/enforce-retention")
    public Response enforceRetention(@HeaderParam("X-Maintenance-Key") String key) {
        assertAuthorized(key);
        int deleted = gdprService.enforceRetention(config.retention().inactiveMonths());
        LOG.infof("Maintenance: retention enforcement deleted %d inactive profiles", deleted);
        return Response.ok(Map.of("action", "enforce-retention", "deleted", deleted)).build();
    }

    @POST
    @Path("/run-clustering")
    public Response runClustering(@HeaderParam("X-Maintenance-Key") String key) {
        assertAuthorized(key);
        clusteringScheduler.runClustering();
        return Response.ok(Map.of("action", "run-clustering", "status", "completed")).build();
    }

    @POST
    @Path("/ldap-import/preview")
    public Response ldapPreview(@HeaderParam("X-Maintenance-Key") String key,
                                @QueryParam("limit") @DefaultValue("20") @Min(1) @Max(200) int limit) {
        assertAuthorized(key);
        try {
            if (maintenanceService != null) {
                return Response.ok(maintenanceService.previewLdapUsers(limit)).build();
            }
            return Response.ok(ldapImportService.preview(Math.min(limit, 200))).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ProblemDetail.of(400, "Configuration Error", "LDAP configuration is invalid"))
                    .build();
        }
    }

    @POST
    @Path("/ldap-import")
    public Response ldapImport(@HeaderParam("X-Maintenance-Key") String key) {
        assertAuthorized(key);
        try {
            if (maintenanceService != null) {
                return Response.ok(maintenanceService.importFromLdap()).build();
            }
            UUID actorId = UUID.fromString("00000000-0000-0000-0000-000000000001");
            return Response.ok(ldapImportService.importFromLdap(actorId)).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ProblemDetail.of(400, "Configuration Error", "LDAP configuration is invalid"))
                    .build();
        }
    }

    @POST
    @Path("/regenerate-embeddings")
    public Response regenerateEmbeddings(@HeaderParam("X-Maintenance-Key") String key,
                                         @QueryParam("nodeType") String nodeTypeParam,
                                         @QueryParam("onlyMissing") @DefaultValue("true") boolean onlyMissing,
                                         @QueryParam("batchSize") @DefaultValue("1") @Min(1) @Max(1000) int batchSize) {
        assertAuthorized(key);
        try {
            if (maintenanceService != null) {
                var status = maintenanceService.startEmbeddingRegeneration(nodeTypeParam, onlyMissing, batchSize);
                return Response.accepted(status).build();
            }
            UUID actorId = UUID.fromString("00000000-0000-0000-0000-000000000001");
            var status = nodeEmbeddingMaintenanceService.startRegenerationEmbeddings(
                    actorId,
                    parseNodeType(nodeTypeParam),
                    onlyMissing,
                    batchSize
            );
            return Response.accepted(status).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ProblemDetail.of(400, "Validation Error", "Invalid maintenance request"))
                    .build();
        }
    }

    @GET
    @Path("/regenerate-embeddings/{jobId}")
    public Response regenerateEmbeddingsStatus(@HeaderParam("X-Maintenance-Key") String key,
                                               @PathParam("jobId") String jobIdParam) {
        assertAuthorized(key);
        try {
            if (maintenanceService != null) {
                return maintenanceService.getEmbeddingRegenerationStatus(jobIdParam)
                        .<Response>map(status -> Response.ok(status).build())
                        .orElseGet(() -> Response.status(Response.Status.NOT_FOUND)
                                .entity(ProblemDetail.of(404, "Not Found", "Embedding job not found"))
                                .build());
            }
            UUID jobId = UUID.fromString(jobIdParam);
            return nodeEmbeddingMaintenanceService.getRegenerationJobStatus(jobId)
                    .<Response>map(status -> Response.ok(status).build())
                    .orElseGet(() -> Response.status(Response.Status.NOT_FOUND)
                            .entity(ProblemDetail.of(404, "Not Found", "Embedding job not found"))
                            .build());
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ProblemDetail.of(400, "Validation Error", "Invalid jobId format"))
                    .build();
        }
    }

    private org.peoplemesh.domain.enums.NodeType parseNodeType(String nodeTypeParam) {
        if (nodeTypeParam == null || nodeTypeParam.isBlank()) {
            return null;
        }
        try {
            return org.peoplemesh.domain.enums.NodeType.valueOf(nodeTypeParam.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid nodeType");
        }
    }

    private void assertAuthorized(String key) {
        MaintenanceAuthHelper.assertAuthorized(key, config, httpHeaders);
    }
}
