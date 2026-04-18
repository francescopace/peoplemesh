package org.peoplemesh.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.domain.model.SkillAssessment;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class SkillAssessmentRepositoryTest {

    @Mock
    EntityManager em;

    @InjectMocks
    SkillAssessmentRepository repository;

    @Test
    void findByNodeIds_empty_returnsEmptyMap() {
        assertTrue(repository.findByNodeIds(List.of()).isEmpty());
    }

    @Test
    void findByNodeIds_groupsByNode() {
        UUID node = UUID.randomUUID();
        SkillAssessment assessment = assessment(node, UUID.randomUUID(), (short) 3);

        TypedQuery<SkillAssessment> query = typedQuery(List.of(assessment));
        when(em.createQuery(anyString(), eq(SkillAssessment.class))).thenReturn(query);

        Map<UUID, List<SkillAssessment>> grouped = repository.findByNodeIds(List.of(node));
        assertEquals(1, grouped.get(node).size());
    }

    @Test
    void findByNodeAsMap_mapsBySkill() {
        UUID node = UUID.randomUUID();
        UUID skill = UUID.randomUUID();
        SkillAssessment assessment = assessment(node, skill, (short) 2);
        TypedQuery<SkillAssessment> query = typedQuery(List.of(assessment));
        when(em.createQuery(anyString(), eq(SkillAssessment.class))).thenReturn(query);

        Map<UUID, SkillAssessment> map = repository.findByNodeAsMap(node);
        assertEquals(assessment, map.get(skill));
    }

    @Test
    void findByNodeAndSkill_returnsFirst() {
        SkillAssessment assessment = assessment(UUID.randomUUID(), UUID.randomUUID(), (short) 4);
        @SuppressWarnings("unchecked")
        TypedQuery<SkillAssessment> query = mock(TypedQuery.class);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultStream()).thenReturn(List.of(assessment).stream());
        when(em.createQuery(anyString(), eq(SkillAssessment.class))).thenReturn(query);

        assertTrue(repository.findByNodeAndSkill(assessment.nodeId, assessment.skillId).isPresent());
    }

    @Test
    void persist_persistsOrMergesByCompositeKey() {
        SkillAssessment noKey = new SkillAssessment();
        SkillAssessment withKey = assessment(UUID.randomUUID(), UUID.randomUUID(), (short) 1);

        repository.persist(noKey);
        repository.persist(withKey);

        verify(em).persist(noKey);
        verify(em).merge(withKey);
    }

    private static TypedQuery<SkillAssessment> typedQuery(List<SkillAssessment> rows) {
        @SuppressWarnings("unchecked")
        TypedQuery<SkillAssessment> query = mock(TypedQuery.class);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(rows);
        return query;
    }

    private static SkillAssessment assessment(UUID nodeId, UUID skillId, short level) {
        SkillAssessment assessment = new SkillAssessment();
        assessment.nodeId = nodeId;
        assessment.skillId = skillId;
        assessment.level = level;
        return assessment;
    }
}
