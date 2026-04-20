package org.peoplemesh.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.repository.NodeRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NodeEmbeddingMaintenanceServiceTest {

    @Mock
    EmbeddingService embeddingService;

    @Mock
    AuditService auditService;

    @Mock
    NodeRepository nodeRepository;

    @InjectMocks
    NodeEmbeddingMaintenanceService service;

    @Test
    void getRegenerationJobStatus_returnsEmptyWhenMissing() {
        assertEquals(Optional.empty(), service.getRegenerationJobStatus(UUID.randomUUID()));
    }

    @Test
    void startRegenerationEmbeddings_rejectsOutOfRangeBatchSize() {
        UUID actorId = UUID.randomUUID();

        IllegalArgumentException tooLow = assertThrows(
                IllegalArgumentException.class,
                () -> service.startRegenerationEmbeddings(actorId, NodeType.USER, false, 0)
        );
        assertTrue(tooLow.getMessage().contains("batchSize must be between 1 and 32"));

        IllegalArgumentException tooHigh = assertThrows(
                IllegalArgumentException.class,
                () -> service.startRegenerationEmbeddings(actorId, NodeType.USER, false, 33)
        );
        assertTrue(tooHigh.getMessage().contains("batchSize must be between 1 and 32"));
    }

    @Test
    void startRegenerationEmbeddings_withNoNodes_completesAndAudits() throws InterruptedException {
        UUID actorId = UUID.randomUUID();
        when(nodeRepository.findNodeIds(NodeType.USER, false)).thenReturn(List.of());

        NodeEmbeddingMaintenanceService.EmbeddingRegenerationJobStatus status = service.startRegenerationEmbeddings(
                actorId,
                NodeType.USER,
                false,
                2
        );
        NodeEmbeddingMaintenanceService.EmbeddingRegenerationJobStatus finalStatus = awaitTerminalStatus(status.jobId());

        assertEquals(NodeEmbeddingMaintenanceService.JobState.COMPLETED, finalStatus.status());
        assertEquals("USER", finalStatus.nodeType());
        assertFalse(finalStatus.onlyMissing());
        assertEquals(0, finalStatus.total());
        assertEquals(0, finalStatus.processed());
        assertEquals(0, finalStatus.succeeded());
        assertEquals(0, finalStatus.failed());
        assertNotNull(finalStatus.startedAt());
        assertNotNull(finalStatus.finishedAt());
        assertTrue(Duration.between(finalStatus.createdAt(), finalStatus.finishedAt()).toMillis() >= 0);

        verifyNoInteractions(embeddingService);
        verify(auditService, timeout(500)).log(
                eq(actorId),
                eq("MAINTENANCE_REGENERATE_EMBEDDINGS"),
                eq("maintenance_regenerate_embeddings"),
                isNull(),
                contains("\"status\":\"COMPLETED\"")
        );
    }

    @Test
    void startRegenerationEmbeddings_whenBatchFails_usesSingleFallbackAndTracksFailures() throws InterruptedException {
        UUID actorId = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(nodeRepository.findNodeIds(NodeType.USER, true)).thenReturn(List.of(first, second));

        NodeEmbeddingMaintenanceService.EmbeddingRegenerationJobStatus status = service.startRegenerationEmbeddings(
                actorId,
                NodeType.USER,
                true,
                2
        );
        NodeEmbeddingMaintenanceService.EmbeddingRegenerationJobStatus finalStatus = awaitTerminalStatus(status.jobId());

        assertEquals(NodeEmbeddingMaintenanceService.JobState.COMPLETED, finalStatus.status());
        assertEquals(2, finalStatus.total());
        assertEquals(2, finalStatus.processed());
        assertEquals(0, finalStatus.succeeded());
        assertEquals(2, finalStatus.failed());
        assertNull(finalStatus.error());

        verify(auditService, timeout(500)).log(
                eq(actorId),
                eq("MAINTENANCE_REGENERATE_EMBEDDINGS"),
                eq("maintenance_regenerate_embeddings"),
                isNull(),
                contains("\"failed\":2")
        );
    }

    @Test
    void startRegenerationEmbeddings_withNullNodeType_usesAllLabel() throws InterruptedException {
        UUID actorId = UUID.randomUUID();
        when(nodeRepository.findNodeIds(null, true)).thenReturn(List.of());

        NodeEmbeddingMaintenanceService.EmbeddingRegenerationJobStatus status = service.startRegenerationEmbeddings(
                actorId,
                null,
                true,
                1
        );
        NodeEmbeddingMaintenanceService.EmbeddingRegenerationJobStatus finalStatus = awaitTerminalStatus(status.jobId());

        assertEquals("ALL", finalStatus.nodeType());
        assertEquals(NodeEmbeddingMaintenanceService.JobState.COMPLETED, finalStatus.status());
    }

    private NodeEmbeddingMaintenanceService.EmbeddingRegenerationJobStatus awaitTerminalStatus(UUID jobId)
            throws InterruptedException {
        Instant timeout = Instant.now().plusSeconds(2);
        while (Instant.now().isBefore(timeout)) {
            Optional<NodeEmbeddingMaintenanceService.EmbeddingRegenerationJobStatus> statusOpt =
                    service.getRegenerationJobStatus(jobId);
            if (statusOpt.isPresent()) {
                NodeEmbeddingMaintenanceService.EmbeddingRegenerationJobStatus status = statusOpt.get();
                if (status.status() == NodeEmbeddingMaintenanceService.JobState.COMPLETED
                        || status.status() == NodeEmbeddingMaintenanceService.JobState.FAILED) {
                    return status;
                }
            }
            Thread.sleep(10);
        }
        fail("Job did not reach terminal state");
        return null;
    }
}
