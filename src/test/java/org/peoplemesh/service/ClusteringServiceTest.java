package org.peoplemesh.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.peoplemesh.config.AppConfig;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClusteringServiceTest {

    @Mock
    AppConfig config;

    @Mock
    AppConfig.ClusteringConfig clusteringConfig;

    @Mock
    EntityManager em;

    @InjectMocks
    ClusteringService service;

    @BeforeEach
    void setUp() {
        when(config.clustering()).thenReturn(clusteringConfig);
        when(clusteringConfig.minClusterSize()).thenReturn(2);
        when(clusteringConfig.maxCentroidDistance()).thenReturn(0.5);
    }

    @Test
    void cosineDistance_identicalVectors_returnsZero() {
        float[] v = {1f, 2f, 3f};
        assertEquals(0.0, service.cosineDistance(v, v), 1e-6);
    }

    @Test
    void cosineDistance_orthogonalVectors_returnsOne() {
        float[] a = {1f, 0f, 0f};
        float[] b = {0f, 1f, 0f};
        assertEquals(1.0, service.cosineDistance(a, b), 1e-6);
    }

    @Test
    void cosineDistance_oppositeVectors_returnsTwo() {
        float[] a = {1f, 0f, 0f};
        float[] b = {-1f, 0f, 0f};
        assertEquals(2.0, service.cosineDistance(a, b), 1e-6);
    }

    @Test
    void cosineDistance_zeroVector_returnsOne() {
        float[] zero = {0f, 0f, 0f};
        float[] v = {1f, 2f, 3f};
        assertEquals(1.0, service.cosineDistance(zero, v), 1e-6);
        assertEquals(1.0, service.cosineDistance(v, zero), 1e-6);
    }

    @Test
    void cosineDistance_partiallyOverlapping_returnsIntermediate() {
        float[] a = {1f, 1f, 0f};
        float[] b = {1f, 0f, 0f};
        double expected = 1.0 - 1.0 / Math.sqrt(2.0);
        assertEquals(expected, service.cosineDistance(a, b), 1e-6);
    }

    @Test
    void kMeans_emptyData_returnsEmptyList() {
        assertTrue(service.kMeans(List.of(), 2, 10).isEmpty());
    }

    @Test
    void kMeans_singleCluster_returnsOneCluster() {
        List<ClusteringService.EmbeddingRow> data = List.of(
                row(new float[]{1f, 0f, 0f}),
                row(new float[]{1f, 0.1f, 0f}),
                row(new float[]{1f, -0.1f, 0f})
        );
        List<ClusteringService.ClusterResult> results = service.kMeans(data, 1, 30);
        assertEquals(1, results.size());
        assertEquals(3, results.getFirst().memberUserIds().size());
    }

    @Test
    void kMeans_twoClusters_separatesDistantVectors() {
        List<ClusteringService.EmbeddingRow> data = List.of(
                row(new float[]{1f, 0f, 0f}),
                row(new float[]{1f, 0.1f, 0f}),
                row(new float[]{1f, -0.1f, 0f}),
                row(new float[]{0f, 1f, 0f}),
                row(new float[]{0f, 1f, 0.1f}),
                row(new float[]{0f, 1f, -0.1f})
        );
        List<ClusteringService.ClusterResult> results = service.kMeans(data, 2, 50);
        assertEquals(2, results.size());
        int totalMembers = results.stream().mapToInt(r -> r.memberUserIds().size()).sum();
        assertEquals(6, totalMembers);
        assertTrue(results.stream().allMatch(r -> r.memberUserIds().size() == 3));
    }

    @Test
    void kMeans_kGreaterThanData_clampedToDataSize() {
        when(clusteringConfig.minClusterSize()).thenReturn(1);
        when(clusteringConfig.maxCentroidDistance()).thenReturn(1.0);
        List<ClusteringService.EmbeddingRow> data = List.of(
                row(new float[]{1f, 0f, 0f}),
                row(new float[]{1f, 0.01f, 0f}),
                row(new float[]{1f, -0.01f, 0f})
        );
        List<ClusteringService.ClusterResult> results = service.kMeans(data, 10, 40);
        int totalMembers = results.stream().mapToInt(r -> r.memberUserIds().size()).sum();
        assertEquals(3, totalMembers);
    }

    @Test
    void kMeans_zeroK_returnsEmptyList() {
        List<ClusteringService.EmbeddingRow> data = List.of(row(new float[]{1f, 0f, 0f}));
        assertTrue(service.kMeans(data, 0, 10).isEmpty());
    }

    @Test
    void parseVectorString_null_returnsNull() throws Exception {
        Method m = ClusteringService.class.getDeclaredMethod("parseVectorString", String.class);
        m.setAccessible(true);
        assertNull(m.invoke(service, new Object[]{null}));
    }

    @Test
    void parseVectorString_bracketed_parsesCorrectly() throws Exception {
        Method m = ClusteringService.class.getDeclaredMethod("parseVectorString", String.class);
        m.setAccessible(true);
        float[] result = (float[]) m.invoke(service, "[1.0,2.0,3.0]");
        assertArrayEquals(new float[]{1f, 2f, 3f}, result, 1e-6f);
    }

    @Test
    void parseVectorString_unbracketed_parsesCorrectly() throws Exception {
        Method m = ClusteringService.class.getDeclaredMethod("parseVectorString", String.class);
        m.setAccessible(true);
        float[] result = (float[]) m.invoke(service, "1.0, 2.0 , 3.0");
        assertArrayEquals(new float[]{1f, 2f, 3f}, result, 1e-6f);
    }

    @Test
    void collectArray_null_doesNothing() throws Exception {
        Method m = ClusteringService.class.getDeclaredMethod("collectArray", List.class, Object.class);
        m.setAccessible(true);
        List<String> target = new ArrayList<>(List.of("x"));
        m.invoke(service, target, null);
        assertEquals(List.of("x"), target);
    }

    @Test
    void collectArray_stringArray_addsAll() throws Exception {
        Method m = ClusteringService.class.getDeclaredMethod("collectArray", List.class, Object.class);
        m.setAccessible(true);
        List<String> target = new ArrayList<>();
        m.invoke(service, target, new String[]{"a", "b"});
        assertEquals(List.of("a", "b"), target);
    }

    @Test
    void collectArray_list_addsAll() throws Exception {
        Method m = ClusteringService.class.getDeclaredMethod("collectArray", List.class, Object.class);
        m.setAccessible(true);
        List<String> target = new ArrayList<>();
        List<Object> input = new ArrayList<>();
        input.add(1);
        input.add("two");
        input.add(null);
        m.invoke(service, target, input);
        assertEquals(List.of("1", "two"), target);
    }

    @Test
    void collectArray_objectArray_addsStringified() throws Exception {
        Method m = ClusteringService.class.getDeclaredMethod("collectArray", List.class, Object.class);
        m.setAccessible(true);
        List<String> target = new ArrayList<>();
        m.invoke(service, target, new Object[]{Integer.valueOf(7), null, Boolean.TRUE});
        assertEquals(List.of("7", "true"), target);
    }

    private static ClusteringService.EmbeddingRow row(float[] embedding) {
        return new ClusteringService.EmbeddingRow(UUID.randomUUID(), embedding);
    }
}
