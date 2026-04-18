package org.peoplemesh.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.domain.dto.ClusterName;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.model.MeshNode;
import org.peoplemesh.repository.NodeRepository;
import org.peoplemesh.service.ClusteringService.ClusterResult;
import org.peoplemesh.service.ClusteringService.EmbeddingRow;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClusteringSchedulerTest {

    @Mock AppConfig config;
    @Mock ClusteringService clusteringService;
    @Mock ClusterNamingLlm clusterNamingLlm;
    @Mock EmbeddingService embeddingService;
    @Mock NodeRepository nodeRepository;

    @InjectMocks
    ClusteringScheduler scheduler;

    AppConfig.ClusteringConfig clusteringConfig;

    @BeforeEach
    void setUp() {
        clusteringConfig = mock(AppConfig.ClusteringConfig.class);
        lenient().when(config.clustering()).thenReturn(clusteringConfig);
        lenient().when(nodeRepository.listCommunities()).thenReturn(List.of());
    }

    @Test
    void runClustering_disabled_doesNothing() {
        when(clusteringConfig.enabled()).thenReturn(false);

        scheduler.runClustering();

        verify(clusteringService, never()).loadPublishedEmbeddings();
    }

    @Test
    void runClustering_notEnoughProfiles_skips() {
        when(clusteringConfig.enabled()).thenReturn(true);
        when(clusteringConfig.minClusterSize()).thenReturn(5);
        when(clusteringService.loadPublishedEmbeddings())
                .thenReturn(List.of(mock(EmbeddingRow.class), mock(EmbeddingRow.class)));

        scheduler.runClustering();

        verify(clusteringService, never()).kMeans(anyList(), anyInt(), anyInt());
    }

    @Test
    void runClustering_withClusters_invokesNaming() {
        when(clusteringConfig.enabled()).thenReturn(true);
        when(clusteringConfig.minClusterSize()).thenReturn(2);
        when(clusteringConfig.k()).thenReturn(3);

        List<EmbeddingRow> embeddings = new ArrayList<>();
        for (int i = 0; i < 10; i++) embeddings.add(mock(EmbeddingRow.class));
        when(clusteringService.loadPublishedEmbeddings()).thenReturn(embeddings);

        ClusterResult cluster = new ClusterResult(new float[]{1f, 2f}, List.of(UUID.randomUUID()));
        when(clusteringService.kMeans(anyList(), eq(3), eq(100)))
                .thenReturn(List.of(cluster));
        when(clusteringService.extractClusterTraits(anyList(), eq(10)))
                .thenReturn(Map.of("skills", List.of("Java")));

        when(clusterNamingLlm.generateName(anyMap())).thenReturn(Optional.empty());

        scheduler.runClustering();

        verify(clusterNamingLlm).generateName(anyMap());
        verify(embeddingService, never()).generateEmbedding(anyString());
    }

    @Test
    void runClustering_namingReturnsEmpty_skipsCluster() {
        when(clusteringConfig.enabled()).thenReturn(true);
        when(clusteringConfig.minClusterSize()).thenReturn(2);
        when(clusteringConfig.k()).thenReturn(2);

        List<EmbeddingRow> embeddings = new ArrayList<>();
        for (int i = 0; i < 5; i++) embeddings.add(mock(EmbeddingRow.class));
        when(clusteringService.loadPublishedEmbeddings()).thenReturn(embeddings);

        ClusterResult cluster = new ClusterResult(new float[]{1f}, List.of(UUID.randomUUID()));
        when(clusteringService.kMeans(anyList(), eq(2), eq(100)))
                .thenReturn(List.of(cluster));
        when(clusteringService.extractClusterTraits(anyList(), eq(10)))
                .thenReturn(Map.of());

        when(clusterNamingLlm.generateName(anyMap())).thenReturn(Optional.empty());

        scheduler.runClustering();

        verify(embeddingService, never()).generateEmbedding(anyString());
    }

    @Test
    void runClustering_exceptionCaught_doesNotPropagate() {
        when(clusteringConfig.enabled()).thenReturn(true);
        when(clusteringService.loadPublishedEmbeddings())
                .thenThrow(new RuntimeException("DB down"));

        scheduler.runClustering();
    }

    @Test
    void runClustering_namedClusterWithoutMatch_createsAutoNode() {
        ClusteringScheduler spyScheduler = spy(scheduler);
        MeshNode newNode = new MeshNode();
        doReturn(newNode).when(spyScheduler).newCommunityNode();
        doReturn(List.of()).when(spyScheduler).loadCommunityNodes();
        doNothing().when(spyScheduler).persistNode(any(MeshNode.class));

        when(clusteringConfig.enabled()).thenReturn(true);
        when(clusteringConfig.minClusterSize()).thenReturn(1);
        when(clusteringConfig.k()).thenReturn(1);
        when(clusteringService.loadPublishedEmbeddings()).thenReturn(List.of(mock(EmbeddingRow.class)));
        UUID member = UUID.randomUUID();
        ClusterResult cluster = new ClusterResult(new float[]{1f}, List.of(member));
        when(clusteringService.kMeans(anyList(), eq(1), eq(100))).thenReturn(List.of(cluster));
        when(clusteringService.extractClusterTraits(anyList(), eq(10))).thenReturn(Map.of("skills", List.of("java")));
        when(clusterNamingLlm.generateName(anyMap())).thenReturn(Optional.of(
                new ClusterName("Java Guild", "Community", List.of("java", "backend"))
        ));
        when(embeddingService.generateEmbedding(anyString())).thenReturn(new float[]{0.1f});

        spyScheduler.runClustering();

        verify(spyScheduler).persistNode(newNode);
        assertEquals(member, newNode.createdBy);
        assertEquals(NodeType.COMMUNITY, newNode.nodeType);
        assertEquals("Java Guild", newNode.title);
        assertEquals("Community", newNode.description);
        assertEquals(List.of("java", "backend"), newNode.tags);
        assertTrue(Boolean.TRUE.equals(newNode.structuredData.get("auto_generated")));
        assertEquals(1, newNode.structuredData.get("cluster_size"));
    }

    @Test
    void runClustering_namedClusterWithTagMatch_updatesExistingAutoNode() {
        ClusteringScheduler spyScheduler = spy(scheduler);
        MeshNode existing = new MeshNode();
        existing.nodeType = NodeType.COMMUNITY;
        existing.tags = List.of("java", "backend");
        existing.structuredData = new HashMap<>(Map.of("auto_generated", true));
        doReturn(List.of(existing)).when(spyScheduler).loadCommunityNodes();
        doReturn(Instant.parse("2026-01-01T00:00:00Z")).when(spyScheduler).now();
        doNothing().when(spyScheduler).persistNode(any(MeshNode.class));

        when(clusteringConfig.enabled()).thenReturn(true);
        when(clusteringConfig.minClusterSize()).thenReturn(1);
        when(clusteringConfig.k()).thenReturn(1);
        when(clusteringService.loadPublishedEmbeddings()).thenReturn(List.of(mock(EmbeddingRow.class)));
        ClusterResult cluster = new ClusterResult(new float[]{1f}, List.of(UUID.randomUUID(), UUID.randomUUID()));
        when(clusteringService.kMeans(anyList(), eq(1), eq(100))).thenReturn(List.of(cluster));
        when(clusteringService.extractClusterTraits(anyList(), eq(10))).thenReturn(Map.of("skills", List.of("java")));
        when(clusterNamingLlm.generateName(anyMap())).thenReturn(Optional.of(
                new ClusterName("Java Builders", "Updated", List.of("java", "backend"))
        ));
        when(embeddingService.generateEmbedding(anyString())).thenReturn(new float[]{0.2f});

        spyScheduler.runClustering();

        verify(spyScheduler).persistNode(existing);
        assertEquals("Java Builders", existing.title);
        assertEquals("Updated", existing.description);
        assertEquals(List.of("java", "backend"), existing.tags);
        assertEquals(2, existing.structuredData.get("cluster_size"));
        assertEquals("2026-01-01T00:00:00Z", existing.structuredData.get("last_clustered_at"));
    }
}
