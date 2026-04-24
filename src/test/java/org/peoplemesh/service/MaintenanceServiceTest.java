package org.peoplemesh.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.exception.ValidationBusinessException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaintenanceServiceTest {

    @Mock
    AppConfig config;
    @Mock
    JdbcConsentTokenStore consentTokenStore;
    @Mock
    GdprService gdprService;
    @Mock
    ClusteringScheduler clusteringScheduler;
    @Mock
    NodeEmbeddingMaintenanceService nodeEmbeddingMaintenanceService;

    @InjectMocks
    MaintenanceService service;

    @Test
    void purgeConsentTokens_returnsSummary() {
        when(consentTokenStore.purgeExpired()).thenReturn(3);
        assertEquals(3, service.purgeConsentTokens().get("purged"));
    }

    @Test
    void enforceRetention_usesConfiguredMonths() {
        AppConfig.RetentionConfig retention = mock(AppConfig.RetentionConfig.class);
        when(config.retention()).thenReturn(retention);
        when(retention.inactiveMonths()).thenReturn(12);
        when(gdprService.enforceRetention(12)).thenReturn(4);

        assertEquals(4, service.enforceRetention().get("deleted"));
    }

    @Test
    void runClustering_returnsCompletedStatus() {
        assertEquals("completed", service.runClustering().get("status"));
        verify(clusteringScheduler).runClustering();
    }

    @Test
    void startEmbeddingRegeneration_parsesNodeType() {
        NodeEmbeddingMaintenanceService.EmbeddingRegenerationJobStatus status =
                new NodeEmbeddingMaintenanceService.EmbeddingRegenerationJobStatus(
                        "regenerate-embeddings",
                        UUID.randomUUID(),
                        NodeEmbeddingMaintenanceService.JobState.QUEUED,
                        "USER",
                        true,
                        5,
                        10,
                        0,
                        0,
                        0,
                        null,
                        null,
                        null,
                        null
                );
        when(nodeEmbeddingMaintenanceService.startRegenerationEmbeddings(any(), any(), anyBoolean(), anyInt())).thenReturn(status);

        NodeEmbeddingMaintenanceService.EmbeddingRegenerationJobStatus result =
                service.startEmbeddingRegeneration("user", true, 5);

        assertEquals(NodeType.USER.name(), result.nodeType());
    }

    @Test
    void getEmbeddingRegenerationStatus_invalidJobId_throwsValidationError() {
        assertThrows(ValidationBusinessException.class, () -> service.getEmbeddingRegenerationStatus("not-a-uuid"));
    }

    @Test
    void getEmbeddingRegenerationStatus_delegatesToService() {
        UUID jobId = UUID.randomUUID();
        when(nodeEmbeddingMaintenanceService.getRegenerationJobStatus(jobId)).thenReturn(Optional.empty());
        Optional<NodeEmbeddingMaintenanceService.EmbeddingRegenerationJobStatus> result =
                service.getEmbeddingRegenerationStatus(jobId.toString());
        assertTrue(result.isEmpty());
    }
}
