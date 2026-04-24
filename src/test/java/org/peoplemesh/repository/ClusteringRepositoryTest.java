package org.peoplemesh.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClusteringRepositoryTest {

    @Mock
    EntityManager em;

    @InjectMocks
    ClusteringRepository repository;

    @Test
    void loadPublishedEmbeddings_mapsRowsToTypedProjection() {
        Query query = nativeQueryReturning(List.<Object[]>of(new Object[]{UUID.randomUUID(), "[0.1,0.2]"}));
        when(em.createNativeQuery(anyString())).thenReturn(query);

        List<ClusteringRepository.PublishedEmbeddingRow> rows = repository.loadPublishedEmbeddings();

        assertEquals(1, rows.size());
        assertEquals("[0.1,0.2]", rows.getFirst().embeddingText());
        verify(query).setMaxResults(20_000);
    }

    @Test
    void loadClusterTraits_emptyInput_returnsEmpty() {
        assertTrue(repository.loadClusterTraits(List.of()).isEmpty());
    }

    @Test
    void loadClusterTraits_mapsRowsToTypedProjection() {
        UUID userId = UUID.randomUUID();
        Query query = nativeQueryReturning(List.<Object[]>of(
                new Object[]{
                        new String[]{"java"},
                        new String[]{"hiking"},
                        new String[]{"cycling"},
                        new String[]{"climate"},
                        "IT"
                }
        ));
        when(em.createNativeQuery(anyString())).thenReturn(query);

        List<ClusteringRepository.ClusterTraitsRow> rows = repository.loadClusterTraits(List.of(userId));

        assertEquals(1, rows.size());
        assertEquals("IT", rows.getFirst().country());
        verify(query).setParameter("ids", List.of(userId));
    }

    private static Query nativeQueryReturning(List<Object[]> rows) {
        Query query = mock(Query.class);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.setMaxResults(any(Integer.class))).thenReturn(query);
        when(query.getResultList()).thenReturn(rows);
        return query;
    }
}
