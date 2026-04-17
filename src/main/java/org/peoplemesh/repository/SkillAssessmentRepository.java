package org.peoplemesh.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.peoplemesh.domain.model.SkillAssessment;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class SkillAssessmentRepository {

    @Inject
    EntityManager em;

    public List<SkillAssessment> findByNode(UUID nodeId) {
        return em.createQuery("FROM SkillAssessment a WHERE a.nodeId = :nodeId", SkillAssessment.class)
                .setParameter("nodeId", nodeId)
                .getResultList();
    }

    public Map<UUID, List<SkillAssessment>> findByNodeIds(List<UUID> nodeIds) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return em.createQuery("FROM SkillAssessment a WHERE a.nodeId in :nodeIds", SkillAssessment.class)
                .setParameter("nodeIds", nodeIds)
                .getResultList().stream()
                .collect(Collectors.groupingBy(sa -> sa.nodeId));
    }

    public Map<UUID, SkillAssessment> findByNodeAsMap(UUID nodeId) {
        return findByNode(nodeId).stream()
                .collect(Collectors.toMap(a -> a.skillId, Function.identity(), (a, b) -> a));
    }

    public Optional<SkillAssessment> findByNodeAndSkill(UUID nodeId, UUID skillId) {
        return em.createQuery(
                        "FROM SkillAssessment a WHERE a.nodeId = :nodeId AND a.skillId = :skillId",
                        SkillAssessment.class)
                .setParameter("nodeId", nodeId)
                .setParameter("skillId", skillId)
                .getResultStream()
                .findFirst();
    }

    public void persist(SkillAssessment assessment) {
        if (assessment.nodeId == null || assessment.skillId == null) {
            em.persist(assessment);
            return;
        }
        em.merge(assessment);
    }
}
