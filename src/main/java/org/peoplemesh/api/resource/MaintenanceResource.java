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
import org.peoplemesh.domain.exception.BusinessException;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.domain.exception.NotFoundBusinessException;
import org.peoplemesh.domain.exception.ValidationBusinessException;
import org.peoplemesh.security.MaintenanceAccessGuard;
import org.peoplemesh.service.MaintenanceService;

import java.util.function.Supplier;

@Path("/api/v1/maintenance")
@Produces(MediaType.APPLICATION_JSON)
@Blocking
public class MaintenanceResource {

    @Inject
    AppConfig config;

    @Inject
    MaintenanceService maintenanceService;

    @Context
    HttpHeaders httpHeaders;

    @POST
    @Path("/purge-consent-tokens")
    public Response purgeConsentTokens(@HeaderParam("X-Maintenance-Key") String key) {
        assertAuthorized(key);
        return Response.ok(maintenanceService.purgeConsentTokens()).build();
    }

    @POST
    @Path("/enforce-retention")
    public Response enforceRetention(@HeaderParam("X-Maintenance-Key") String key) {
        assertAuthorized(key);
        return Response.ok(maintenanceService.enforceRetention()).build();
    }

    @POST
    @Path("/run-clustering")
    public Response runClustering(@HeaderParam("X-Maintenance-Key") String key) {
        assertAuthorized(key);
        return Response.ok(maintenanceService.runClustering()).build();
    }

    @POST
    @Path("/ldap-import/preview")
    public Response ldapPreview(@HeaderParam("X-Maintenance-Key") String key,
                                @QueryParam("limit") @DefaultValue("20") @Min(1) @Max(200) int limit) {
        assertAuthorized(key);
        return mapValidationError(
                () -> Response.ok(maintenanceService.previewLdapUsers(limit)).build(),
                "Configuration Error",
                "LDAP configuration is invalid");
    }

    @POST
    @Path("/ldap-import")
    public Response ldapImport(@HeaderParam("X-Maintenance-Key") String key) {
        assertAuthorized(key);
        return mapValidationError(
                () -> Response.ok(maintenanceService.importFromLdap()).build(),
                "Configuration Error",
                "LDAP configuration is invalid");
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
        return mapValidationError(
                () -> Response.accepted(maintenanceService.startEmbeddingRegeneration(
                        nodeTypeParam, onlyMissing, batchSize
                )).build(),
                "Validation Error",
                "Invalid maintenance request");
    }

    @GET
    @Path("/regenerate-embeddings/{jobId}")
    public Response regenerateEmbeddingsStatus(@HeaderParam("X-Maintenance-Key") String key,
                                               @PathParam("jobId")
                                               @Pattern(regexp = "^[0-9a-fA-F\\-]{36}$", message = "jobId must be a UUID")
                                               String jobIdParam) {
        assertAuthorized(key);
        return mapValidationError(
                () -> {
                    var status = maintenanceService.getEmbeddingRegenerationStatus(jobIdParam)
                            .orElseThrow(() -> new NotFoundBusinessException("Embedding job not found"));
                    return Response.ok(status).build();
                },
                "Validation Error",
                "Invalid jobId format");
    }

    private Response mapValidationError(Supplier<Response> action, String title, String detail) {
        try {
            return action.get();
        } catch (ValidationBusinessException e) {
            throw new BusinessException(400, title, detail);
        }
    }

    private void assertAuthorized(String key) {
        MaintenanceAccessGuard.assertAuthorized(key, config, httpHeaders);
    }
}
