package org.peoplemesh.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.domain.enums.NodeType;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeshNodeSearchRepositoryTest {

    @Mock
    EntityManager em;

    @InjectMocks
    MeshNodeSearchRepository repository;

    @Test
    void findUserCandidatesByEmbedding_bindsAllParameters() {
        Query query = nativeQueryReturning(List.<Object[]>of(new Object[]{UUID.randomUUID()}));
        when(em.createNativeQuery(anyString())).thenReturn(query);

        List<Object[]> result = repository.findUserCandidatesByEmbedding(new float[]{0.1f}, UUID.randomUUID(), 10);

        assertEquals(1, result.size());
        verify(query, times(3)).setParameter(anyString(), any());
    }

    @Test
    void findNodeCandidatesByEmbedding_withAndWithoutType() {
        Query query = nativeQueryReturning(List.of());
        when(em.createNativeQuery(anyString())).thenReturn(query);

        repository.findNodeCandidatesByEmbedding(new float[]{0.1f}, UUID.randomUUID(), NodeType.JOB, 10);
        repository.findNodeCandidatesByEmbedding(new float[]{0.1f}, UUID.randomUUID(), null, 10);

        verify(query, times(2)).setParameter("poolSize", 10);
    }

    @Test
    void unifiedVectorSearch_buildsCountryAndLanguageFilters() {
        Query query = nativeQueryReturning(List.<Object[]>of(new Object[]{UUID.randomUUID()}));
        when(em.createNativeQuery(anyString())).thenReturn(query);

        List<Object[]> result = repository.unifiedVectorSearch(
                new float[]{0.2f},
                UUID.randomUUID(),
                "it",
                List.of("English", "Italian"),
                20
        );

        assertEquals(1, result.size());
        verify(query).setParameter("countryFilter", "IT");
        verify(query).setParameter("poolSize", 20);
    }

    private static Query nativeQueryReturning(List<Object[]> rows) {
        Query query = mock(Query.class);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(rows);
        return query;
    }
}
