package org.peoplemesh.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.domain.dto.LdapImportResult;
import org.peoplemesh.domain.dto.LdapUserPreview;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.exception.ValidationBusinessException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class MaintenanceService {

    private static final Logger LOG = Logger.getLogger(MaintenanceService.class);
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

    public Map<String, Object> purgeConsentTokens() {
        int purged = consentTokenStore.purgeExpired();
        LOG.infof("Maintenance: purged %d expired consent tokens", purged);
        return Map.of("action", "purge-consent-tokens", "purged", purged);
    }

    public Map<String, Object> enforceRetention() {
        int deleted = gdprService.enforceRetention(config.retention().inactiveMonths());
        LOG.infof("Maintenance: retention enforcement deleted %d inactive profiles", deleted);
        return Map.of("action", "enforce-retention", "deleted", deleted);
    }

    public Map<String, Object> runClustering() {
        clusteringScheduler.runClustering();
        return Map.of("action", "run-clustering", "status", "completed");
    }

    public List<LdapUserPreview> previewLdapUsers(int limit) {
        try {
            return ldapImportService.preview(Math.min(limit, 200));
        } catch (IllegalStateException e) {
            throw new ValidationBusinessException("LDAP configuration is invalid");
        }
    }

    public LdapImportResult importFromLdap() {
        try {
            return ldapImportService.importFromLdap(MAINTENANCE_ACTOR_ID);
        } catch (IllegalStateException e) {
            throw new ValidationBusinessException("LDAP configuration is invalid");
        }
    }

    public NodeEmbeddingMaintenanceService.EmbeddingRegenerationJobStatus startEmbeddingRegeneration(
            String nodeTypeParam,
            boolean onlyMissing,
            int batchSize
    ) {
        NodeType nodeType = parseNodeType(nodeTypeParam);
        return nodeEmbeddingMaintenanceService.startRegenerationEmbeddings(
                MAINTENANCE_ACTOR_ID,
                nodeType,
                onlyMissing,
                batchSize
        );
    }

    public Optional<NodeEmbeddingMaintenanceService.EmbeddingRegenerationJobStatus> getEmbeddingRegenerationStatus(
            String jobIdParam
    ) {
        UUID jobId;
        try {
            jobId = UUID.fromString(jobIdParam);
        } catch (IllegalArgumentException e) {
            throw new ValidationBusinessException("Invalid jobId format");
        }
        return nodeEmbeddingMaintenanceService.getRegenerationJobStatus(jobId);
    }

    private NodeType parseNodeType(String nodeTypeParam) {
        if (nodeTypeParam == null || nodeTypeParam.isBlank()) {
            return null;
        }
        try {
            return NodeType.valueOf(nodeTypeParam.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationBusinessException("Invalid nodeType: " + nodeTypeParam);
        }
    }
}
