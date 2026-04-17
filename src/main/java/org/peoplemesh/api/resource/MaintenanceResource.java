package org.peoplemesh.api.resource;

import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
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
import org.peoplemesh.api.MaintenanceAuthHelper;
import org.peoplemesh.api.error.ProblemDetail;
import org.peoplemesh.application.MaintenanceApplicationService;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.domain.exception.NotFoundBusinessException;
import org.peoplemesh.service.ClusteringScheduler;
import org.peoplemesh.service.GdprService;
import org.peoplemesh.service.JdbcConsentTokenStore;
import org.peoplemesh.service.MaintenanceService;

@Path("/api/v1/maintenance")
@Produces(MediaType.APPLICATION_JSON)
@Blocking
public class MaintenanceResource {

    @Inject
    AppConfig config;

    @Inject
    MaintenanceApplicationService maintenanceApplicationService;

    @Inject
    JdbcConsentTokenStore consentTokenStore;

    @Inject
    GdprService gdprService;

    @Inject
    ClusteringScheduler clusteringScheduler;

    @Inject
    MaintenanceService maintenanceService;

    @Context
    HttpHeaders httpHeaders;

    @POST
    @Path("/purge-consent-tokens")
    public Response purgeConsentTokens(@HeaderParam("X-Maintenance-Key") String key) {
        assertAuthorized(key);
        if (maintenanceApplicationService != null) {
            return Response.ok(maintenanceApplicationService.purgeConsentTokens()).build();
        }
        return Response.ok(java.util.Map.of("action", "purge-consent-tokens", "purged", consentTokenStore.purgeExpired())).build();
    }

    @POST
    @Path("/enforce-retention")
    public Response enforceRetention(@HeaderParam("X-Maintenance-Key") String key) {
        assertAuthorized(key);
        if (maintenanceApplicationService != null) {
            return Response.ok(maintenanceApplicationService.enforceRetention()).build();
        }
        return Response.ok(java.util.Map.of(
                "action",
                "enforce-retention",
                "deleted",
                gdprService.enforceRetention(config.retention().inactiveMonths())
        )).build();
    }

    @POST
    @Path("/run-clustering")
    public Response runClustering(@HeaderParam("X-Maintenance-Key") String key) {
        assertAuthorized(key);
        if (maintenanceApplicationService != null) {
            return Response.ok(maintenanceApplicationService.runClustering()).build();
        }
        clusteringScheduler.runClustering();
        return Response.ok(java.util.Map.of("action", "run-clustering", "status", "completed")).build();
    }

    @POST
    @Path("/ldap-import/preview")
    public Response ldapPreview(@HeaderParam("X-Maintenance-Key") String key,
                                @QueryParam("limit") @DefaultValue("20") @Min(1) @Max(200) int limit) {
        assertAuthorized(key);
        try {
            return Response.ok(maintenanceService.previewLdapUsers(limit)).build();
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
            return Response.ok(maintenanceService.importFromLdap()).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ProblemDetail.of(400, "Configuration Error", "LDAP configuration is invalid"))
                    .build();
        }
    }

    @POST
    @Path("/regenerate-embeddings")
    public Response regenerateEmbeddings(@HeaderParam("X-Maintenance-Key") String key,
                                         @QueryParam("nodeType")
                                         @Pattern(regexp = "^[A-Za-z_]*$", message = "nodeType must be alphabetic")
                                         String nodeTypeParam,
                                         @QueryParam("onlyMissing") @DefaultValue("true") boolean onlyMissing,
                                         @QueryParam("batchSize") @DefaultValue("1") @Min(1) @Max(1000) int batchSize) {
        assertAuthorized(key);
        try {
            return Response.accepted(maintenanceService.startEmbeddingRegeneration(
                    nodeTypeParam, onlyMissing, batchSize
            )).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ProblemDetail.of(400, "Validation Error", "Invalid maintenance request"))
                    .build();
        }
    }

    @GET
    @Path("/regenerate-embeddings/{jobId}")
    public Response regenerateEmbeddingsStatus(@HeaderParam("X-Maintenance-Key") String key,
                                               @PathParam("jobId")
                                               @Pattern(regexp = "^[0-9a-fA-F\\-]{36}$", message = "jobId must be a UUID")
                                               String jobIdParam) {
        assertAuthorized(key);
        try {
            var status = maintenanceService.getEmbeddingRegenerationStatus(jobIdParam)
                    .orElseThrow(() -> new NotFoundBusinessException("Embedding job not found"));
            return Response.ok(status).build();
        } catch (NotFoundBusinessException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(ProblemDetail.of(404, "Not Found", e.publicDetail()))
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ProblemDetail.of(400, "Validation Error", "Invalid jobId format"))
                    .build();
        }
    }

    private void assertAuthorized(String key) {
        MaintenanceAuthHelper.assertAuthorized(key, config, httpHeaders);
    }
}
