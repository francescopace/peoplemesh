package org.peoplemesh.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.domain.dto.LdapImportResult;
import org.peoplemesh.domain.dto.LdapUserPreview;
import org.peoplemesh.service.ClusteringScheduler;
import org.peoplemesh.service.GdprService;
import org.peoplemesh.service.JdbcConsentTokenStore;
import org.peoplemesh.service.MaintenanceService;
import org.peoplemesh.service.NodeEmbeddingMaintenanceService;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class MaintenanceApplicationService {

    private static final Logger LOG = Logger.getLogger(MaintenanceApplicationService.class);

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
        return maintenanceService.previewLdapUsers(limit);
    }

    public LdapImportResult importFromLdap() {
        return maintenanceService.importFromLdap();
    }

    public NodeEmbeddingMaintenanceService.EmbeddingRegenerationJobStatus regenerateEmbeddings(
            String nodeType,
            boolean onlyMissing,
            int batchSize
    ) {
        return maintenanceService.startEmbeddingRegeneration(nodeType, onlyMissing, batchSize);
    }

    public Optional<NodeEmbeddingMaintenanceService.EmbeddingRegenerationJobStatus> embeddingRegenerationStatus(
            String jobId
    ) {
        return maintenanceService.getEmbeddingRegenerationStatus(jobId);
    }
}
