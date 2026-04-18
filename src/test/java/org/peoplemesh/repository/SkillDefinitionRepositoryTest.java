package org.peoplemesh.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.domain.model.SkillDefinition;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class SkillDefinitionRepositoryTest {

    @Mock
    EntityManager em;

    @InjectMocks
    SkillDefinitionRepository repository;

    @Test
    void countAndFindMethods_delegateToEntityManager() {
        @SuppressWarnings("unchecked")
        TypedQuery<Long> longQuery = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(longQuery);
        when(longQuery.setParameter(anyString(), any())).thenReturn(longQuery);
        when(longQuery.getSingleResult()).thenReturn(5L);

        assertEquals(5L, repository.countAll());
        assertEquals(5L, repository.countByCatalog(UUID.randomUUID()));
    }

    @Test
    void findByCatalogAndName_nullName_returnsEmpty() {
        assertTrue(repository.findByCatalogAndName(UUID.randomUUID(), null).isEmpty());
    }

    @Test
    void findByIds_empty_returnsEmpty() {
        assertTrue(repository.findByIds(List.of()).isEmpty());
    }

    @Test
    void findMapByIds_mapsById() {
        SkillDefinition definition = new SkillDefinition();
        definition.id = UUID.randomUUID();

        @SuppressWarnings("unchecked")
        TypedQuery<SkillDefinition> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(SkillDefinition.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(definition));

        Map<UUID, SkillDefinition> map = repository.findMapByIds(List.of(definition.id));
        assertEquals(definition, map.get(definition.id));
    }

    @Test
    void upsert_persistWhenNew_mergeWhenExisting() {
        SkillDefinition newDefinition = new SkillDefinition();
        SkillDefinition existing = new SkillDefinition();
        existing.id = UUID.randomUUID();

        repository.upsert(newDefinition);
        repository.upsert(existing);

        verify(em).persist(newDefinition);
        verify(em).merge(existing);
    }

    @Test
    void countByCatalogIds_emptyInput_returnsEmptyMap() {
        assertTrue(repository.countByCatalogIds(List.of()).isEmpty());
    }

    @Test
    void countByCatalogIds_collectsAggregates() {
        UUID c1 = UUID.randomUUID();
        UUID c2 = UUID.randomUUID();
        @SuppressWarnings("unchecked")
        TypedQuery<Object[]> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(Object[].class))).thenReturn(query);
        when(query.setParameter(anyInt(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(new Object[]{c1, 2L}, new Object[]{c2, 4L}));

        Map<UUID, Long> counts = repository.countByCatalogIds(List.of(c1, c2));

        assertEquals(2L, counts.get(c1));
        assertEquals(4L, counts.get(c2));
    }

    @Test
    void listSkills_appliesPagingForCategoryAndDefaultBranch() {
        @SuppressWarnings("unchecked")
        TypedQuery<SkillDefinition> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(SkillDefinition.class))).thenReturn(query);
        when(query.setParameter(anyInt(), any())).thenReturn(query);
        when(query.setFirstResult(anyInt())).thenReturn(query);
        when(query.setMaxResults(anyInt())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        repository.listSkills(UUID.randomUUID(), "cat", 2, 10);
        repository.listSkills(UUID.randomUUID(), null, 1, 20);

        verify(query, times(2)).setFirstResult(20);
    }

    @Test
    void findSimilarByEmbedding_invalidInput_returnsEmpty() {
        assertTrue(repository.findSimilarByEmbedding(null, 10, 0.7).isEmpty());
        assertTrue(repository.findSimilarByEmbedding(new float[0], 10, 0.7).isEmpty());
        assertTrue(repository.findSimilarByEmbedding(new float[]{0.1f}, 0, 0.7).isEmpty());
    }

    @Test
    void findSimilarByEmbedding_executesNativeQuery() {
        Query query = mock(Query.class, RETURNS_SELF);
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.<Object[]>of(new Object[]{UUID.randomUUID(), "java", null, 0.92}));

        List<Object[]> result = repository.findSimilarByEmbedding(new float[]{0.1f, 0.2f}, 5, 0.8);
        assertEquals(1, result.size());
    }
}
