package org.peoplemesh.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.domain.dto.OperationTimingStatsDto;
import org.peoplemesh.domain.dto.SystemStatisticsDto;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.exception.ForbiddenBusinessException;
import org.peoplemesh.repository.NodeRepository;
import org.peoplemesh.repository.SkillDefinitionRepository;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemStatisticsServiceTest {

    @Mock
    NodeRepository nodeRepository;

    @Mock
    SkillDefinitionRepository skillDefinitionRepository;

    @InjectMocks
    SystemStatisticsService service;

    @Test
    void loadStatistics_aggregatesHnswFromAllSearchTimers() {
        service.meterRegistry = new SimpleMeterRegistry();

        service.meterRegistry.timer("peoplemesh.llm.inference")
                .record(15, TimeUnit.MILLISECONDS);
        service.meterRegistry.timer("peoplemesh.embedding.inference")
                .record(25, TimeUnit.MILLISECONDS);
        service.meterRegistry.timer("peoplemesh.hnsw.search.user")
                .record(20, TimeUnit.MILLISECONDS);
        service.meterRegistry.timer("peoplemesh.hnsw.search.node")
                .record(40, TimeUnit.MILLISECONDS);
        service.meterRegistry.timer("peoplemesh.hnsw.search.unified")
                .record(60, TimeUnit.MILLISECONDS);

        when(nodeRepository.countByType(NodeType.USER)).thenReturn(1L);
        when(nodeRepository.countByType(NodeType.JOB)).thenReturn(1L);
        when(nodeRepository.countAll()).thenReturn(4L);
        when(nodeRepository.countSearchableNodes()).thenReturn(10L);
        when(nodeRepository.countSearchableNodesWithEmbedding()).thenReturn(8L);
        when(skillDefinitionRepository.countAll()).thenReturn(1L);

        SystemStatisticsDto result = service.loadStatistics();

        assertTrue(result.timings().llmInference().sampleCount() >= 1);
        assertTrue(result.timings().embeddingInferenceSingle().sampleCount() >= 1);
        assertTrue(result.timings().embeddingInferenceSingle().maxMs() >= 25);
        assertTrue(result.timings().hnswSearch().sampleCount() >= 3);
        assertTrue(result.timings().hnswSearch().avgMs() > 0);
        assertTrue(result.timings().hnswSearch().maxMs() >= 60);
        assertEquals(10L, result.searchableNodes());
        assertEquals(8L, result.searchableNodesWithEmbedding());
    }

    @Mock
    EntitlementService entitlementService;

    @Test
    void loadStatisticsForUser_nonAdmin_throwsForbidden() {
        UUID userId = UUID.randomUUID();
        when(entitlementService.isAdmin(userId)).thenReturn(false);

        ForbiddenBusinessException ex = assertThrows(
                ForbiddenBusinessException.class,
                () -> service.loadStatisticsForUser(userId)
        );

        assertEquals("Missing entitlement is_admin", ex.getMessage());
    }

    @Test
    void loadStatisticsForUser_admin_returnsStatistics() {
        service.meterRegistry = new SimpleMeterRegistry();
        UUID userId = UUID.randomUUID();
        when(entitlementService.isAdmin(userId)).thenReturn(true);
        when(nodeRepository.countByType(NodeType.USER)).thenReturn(2L);
        when(nodeRepository.countByType(NodeType.JOB)).thenReturn(3L);
        when(nodeRepository.countAll()).thenReturn(10L);
        when(nodeRepository.countSearchableNodes()).thenReturn(0L);
        when(nodeRepository.countSearchableNodesWithEmbedding()).thenReturn(0L);
        when(skillDefinitionRepository.countAll()).thenReturn(5L);

        SystemStatisticsDto result = service.loadStatisticsForUser(userId);

        assertEquals(2L, result.users());
        assertEquals(3L, result.jobs());
        assertEquals(5L, result.others());
        assertEquals(5L, result.skills());
    }

    @Test
    void loadStatistics_withoutTimers_returnsZeroTimingStats() {
        service.meterRegistry = new SimpleMeterRegistry();
        when(nodeRepository.countByType(NodeType.USER)).thenReturn(0L);
        when(nodeRepository.countByType(NodeType.JOB)).thenReturn(0L);
        when(nodeRepository.countAll()).thenReturn(0L);
        when(nodeRepository.countSearchableNodes()).thenReturn(0L);
        when(nodeRepository.countSearchableNodesWithEmbedding()).thenReturn(0L);
        when(skillDefinitionRepository.countAll()).thenReturn(0L);

        SystemStatisticsDto result = service.loadStatistics();

        assertEquals(0L, result.timings().llmInference().sampleCount());
        assertEquals(0L, result.timings().embeddingInferenceSingle().sampleCount());
        assertEquals(0L, result.timings().hnswSearch().sampleCount());
    }

    @Test
    void aggregateTimers_nullInput_throwsIllegalArgumentException() throws Exception {
        Method aggregateTimers = SystemStatisticsService.class.getDeclaredMethod("aggregateTimers", List.class);
        aggregateTimers.setAccessible(true);

        InvocationTargetException ex = assertThrows(
                InvocationTargetException.class,
                () -> aggregateTimers.invoke(service, new Object[]{null})
        );
        assertTrue(ex.getCause() instanceof IllegalArgumentException);
        assertEquals("metricNames is required", ex.getCause().getMessage());
    }

    @Test
    void aggregateTimers_ignoresBlankMetricNames() throws Exception {
        service.meterRegistry = new SimpleMeterRegistry();
        Method aggregateTimers = SystemStatisticsService.class.getDeclaredMethod("aggregateTimers", List.class);
        aggregateTimers.setAccessible(true);

        OperationTimingStatsDto stats = (OperationTimingStatsDto) aggregateTimers.invoke(
                service,
                Arrays.asList("", "   ", null)
        );

        assertEquals(0L, stats.sampleCount());
        assertEquals(0L, stats.avgMs());
        assertEquals(0L, stats.p95Ms());
        assertEquals(0L, stats.maxMs());
    }
}
