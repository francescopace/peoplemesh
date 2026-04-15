package org.peoplemesh.api;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import io.smallrye.common.annotation.Blocking;
import org.jboss.logging.Logger;
import org.peoplemesh.domain.dto.LdapImportResult;
import org.peoplemesh.domain.dto.LdapUserPreview;
import org.peoplemesh.domain.enums.NodeType;

import java.net.InetAddress;
import java.security.MessageDigest;
import java.util.List;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.service.ClusteringScheduler;
import org.peoplemesh.service.GdprService;
import org.peoplemesh.service.JdbcConsentTokenStore;
import org.peoplemesh.service.LdapImportService;
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
    private static final UUID MAINTENANCE_ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Inject
    AppConfig config;

    @Inject
    JdbcConsentTokenStore consentTokenStore;

    @Inject
    GdprService gdprService;

    @Inject
    ClusteringScheduler clusteringScheduler;

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
                                @QueryParam("limit") @DefaultValue("20") int limit) {
        assertAuthorized(key);
        try {
            List<LdapUserPreview> previews = ldapImportService.preview(Math.min(limit, 200));
            return Response.ok(previews).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ProblemDetail.of(400, "Configuration Error", e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/ldap-import")
    public Response ldapImport(@HeaderParam("X-Maintenance-Key") String key) {
        assertAuthorized(key);
        try {
            LdapImportResult result = ldapImportService.importFromLdap(MAINTENANCE_ACTOR_ID);
            return Response.ok(result).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ProblemDetail.of(400, "Configuration Error", e.getMessage()))
                    .build();
        }
    }

    @POST
    @Path("/regenerate-embeddings")
    public Response regenerateEmbeddings(@HeaderParam("X-Maintenance-Key") String key,
                                         @QueryParam("nodeType") String nodeTypeParam,
                                         @QueryParam("onlyMissing") @DefaultValue("true") boolean onlyMissing) {
        assertAuthorized(key);
        try {
            NodeType nodeType = parseNodeType(nodeTypeParam);
            NodeEmbeddingMaintenanceService.EmbeddingRegenerationResult result =
                    nodeEmbeddingMaintenanceService.regenerateEmbeddings(MAINTENANCE_ACTOR_ID, nodeType, onlyMissing);
            return Response.ok(Map.of(
                    "action", "regenerate-embeddings",
                    "nodeType", result.nodeType() == null ? "ALL" : result.nodeType().name(),
                    "onlyMissing", result.onlyMissing(),
                    "processed", result.processed(),
                    "succeeded", result.succeeded(),
                    "failed", result.failed()
            )).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ProblemDetail.of(400, "Validation Error", e.getMessage()))
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(ProblemDetail.of(500, "Embedding Error", e.getMessage()))
                    .build();
        }
    }

    private NodeType parseNodeType(String nodeTypeParam) {
        if (nodeTypeParam == null || nodeTypeParam.isBlank()) {
            return null;
        }
        try {
            return NodeType.valueOf(nodeTypeParam.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid nodeType: " + nodeTypeParam);
        }
    }

    private void assertAuthorized(String key) {
        MaintenanceAuthHelper.assertAuthorized(key, config, httpHeaders);
    }

    static boolean matchesAnyCidr(String ip, List<String> cidrs) {
        try {
            byte[] addr = InetAddress.getByName(ip).getAddress();
            for (String cidr : cidrs) {
                if (cidr.contains("/")) {
                    String[] parts = cidr.split("/", 2);
                    byte[] net = InetAddress.getByName(parts[0]).getAddress();
                    int prefixLen = Integer.parseInt(parts[1]);
                    if (addr.length == net.length && prefixMatch(addr, net, prefixLen)) {
                        return true;
                    }
                } else {
                    byte[] single = InetAddress.getByName(cidr).getAddress();
                    if (MessageDigest.isEqual(addr, single)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            LOG.debugf("CIDR matching failed: %s", e.getMessage());
        }
        return false;
    }

    private static boolean prefixMatch(byte[] addr, byte[] net, int prefixLen) {
        int fullBytes = prefixLen / 8;
        for (int i = 0; i < fullBytes && i < addr.length; i++) {
            if (addr[i] != net[i]) return false;
        }
        int remaining = prefixLen % 8;
        if (remaining > 0 && fullBytes < addr.length) {
            int mask = 0xFF << (8 - remaining);
            if ((addr[fullBytes] & mask) != (net[fullBytes] & mask)) return false;
        }
        return true;
    }
}
