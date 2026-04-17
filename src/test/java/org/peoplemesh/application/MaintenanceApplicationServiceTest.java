package org.peoplemesh.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.service.ClusteringScheduler;
import org.peoplemesh.service.GdprService;
import org.peoplemesh.service.JdbcConsentTokenStore;
import org.peoplemesh.service.MaintenanceService;
import org.peoplemesh.service.NodeEmbeddingMaintenanceService;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaintenanceApplicationServiceTest {

    @Mock
    AppConfig config;
    @Mock
    AppConfig.RetentionConfig retentionConfig;
    @Mock
    JdbcConsentTokenStore consentTokenStore;
    @Mock
    GdprService gdprService;
    @Mock
    ClusteringScheduler clusteringScheduler;
    @Mock
    MaintenanceService maintenanceService;

    @InjectMocks
    MaintenanceApplicationService service;

    @Test
    void purgeConsentTokens_returnsSummary() {
        when(consentTokenStore.purgeExpired()).thenReturn(7);

        Map<String, Object> result = service.purgeConsentTokens();

        assertEquals("purge-consent-tokens", result.get("action"));
        assertEquals(7, result.get("purged"));
    }

    @Test
    void enforceRetention_usesConfiguredMonths() {
        when(config.retention()).thenReturn(retentionConfig);
        when(retentionConfig.inactiveMonths()).thenReturn(9);
        when(gdprService.enforceRetention(9)).thenReturn(3);

        Map<String, Object> result = service.enforceRetention();

        assertEquals("enforce-retention", result.get("action"));
        assertEquals(3, result.get("deleted"));
    }

    @Test
    void runClustering_returnsCompleted() {
        Map<String, Object> result = service.runClustering();

        assertEquals("run-clustering", result.get("action"));
        assertEquals("completed", result.get("status"));
        verify(clusteringScheduler).runClustering();
    }

    @Test
    void ldapMethods_delegateToMaintenanceService() {
        List<?> preview = List.of();
        when(maintenanceService.previewLdapUsers(25)).thenReturn((List) preview);
        when(maintenanceService.importFromLdap()).thenReturn(null);

        assertSame(preview, service.previewLdapUsers(25));
        service.importFromLdap();

        verify(maintenanceService).previewLdapUsers(25);
        verify(maintenanceService).importFromLdap();
    }

    @Test
    void embeddingMethods_delegateToMaintenanceService() {
        NodeEmbeddingMaintenanceService.EmbeddingRegenerationJobStatus status =
                new NodeEmbeddingMaintenanceService.EmbeddingRegenerationJobStatus(
                        "regenerate-embeddings",
                        UUID.randomUUID(),
                        NodeEmbeddingMaintenanceService.JobState.QUEUED,
                        "ALL",
                        true,
                        10,
                        0,
                        0,
                        0,
                        0,
                        null,
                        java.time.Instant.now(),
                        null,
                        null
                );
        when(maintenanceService.startEmbeddingRegeneration("USER", true, 10)).thenReturn(status);
        when(maintenanceService.getEmbeddingRegenerationStatus("job-id")).thenReturn(Optional.of(status));

        assertSame(status, service.regenerateEmbeddings("USER", true, 10));
        assertEquals(Optional.of(status), service.embeddingRegenerationStatus("job-id"));
    }
}
