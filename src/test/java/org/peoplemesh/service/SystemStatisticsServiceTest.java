package org.peoplemesh.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.domain.dto.SystemStatisticsDto;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.repository.NodeRepository;
import org.peoplemesh.repository.SkillDefinitionRepository;

import java.util.List;
import java.util.concurrent.TimeUnit;

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
        service.meterRegistry.timer("peoplemesh.embedding.inference.batch")
                .record(35, TimeUnit.MILLISECONDS);
        service.meterRegistry.timer("peoplemesh.hnsw.search.user")
                .record(20, TimeUnit.MILLISECONDS);
        service.meterRegistry.timer("peoplemesh.hnsw.search.node")
                .record(40, TimeUnit.MILLISECONDS);
        service.meterRegistry.timer("peoplemesh.hnsw.search.unified")
                .record(60, TimeUnit.MILLISECONDS);

        when(nodeRepository.countByType(NodeType.USER)).thenReturn(1L);
        when(nodeRepository.countByType(NodeType.JOB)).thenReturn(1L);
        when(nodeRepository.countByTypes(List.of(NodeType.COMMUNITY, NodeType.INTEREST_GROUP))).thenReturn(1L);
        when(skillDefinitionRepository.countAll()).thenReturn(1L);

        SystemStatisticsDto result = service.loadStatistics();

        assertTrue(result.timings().llmInference().sampleCount() >= 1);
        assertTrue(result.timings().embeddingInferenceSingle().sampleCount() >= 1);
        assertTrue(result.timings().embeddingInferenceSingle().maxMs() >= 25);
        assertTrue(result.timings().embeddingInferenceBatch().sampleCount() >= 1);
        assertTrue(result.timings().embeddingInferenceBatch().maxMs() >= 35);
        assertTrue(result.timings().hnswSearch().sampleCount() >= 3);
        assertTrue(result.timings().hnswSearch().avgMs() > 0);
        assertTrue(result.timings().hnswSearch().maxMs() >= 60);
    }
}
