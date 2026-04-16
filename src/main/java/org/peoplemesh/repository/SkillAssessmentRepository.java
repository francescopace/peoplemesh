package org.peoplemesh.repository;

import jakarta.enterprise.context.ApplicationScoped;
import org.peoplemesh.domain.model.SkillAssessment;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class SkillAssessmentRepository {

    public List<SkillAssessment> findByNode(UUID nodeId) {
        return SkillAssessment.findByNode(nodeId);
    }

    public Map<UUID, List<SkillAssessment>> findByNodeIds(List<UUID> nodeIds) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return SkillAssessment.<SkillAssessment>list("nodeId in ?1", nodeIds).stream()
                .collect(Collectors.groupingBy(sa -> sa.nodeId));
    }

    public Map<UUID, SkillAssessment> findByNodeAsMap(UUID nodeId) {
        return SkillAssessment.findByNode(nodeId).stream()
                .collect(Collectors.toMap(a -> a.skillId, Function.identity(), (a, b) -> a));
    }

    public SkillAssessment findByNodeAndSkill(UUID nodeId, UUID skillId) {
        return SkillAssessment.find("nodeId = ?1 and skillId = ?2", nodeId, skillId).firstResult();
    }

    public void persist(SkillAssessment assessment) {
        assessment.persist();
    }
}
