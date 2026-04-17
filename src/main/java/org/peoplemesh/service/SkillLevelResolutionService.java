package org.peoplemesh.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.peoplemesh.domain.model.SkillAssessment;
import org.peoplemesh.repository.SkillAssessmentRepository;
import org.peoplemesh.repository.SkillDefinitionRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class SkillLevelResolutionService {

    @Inject
    SkillAssessmentRepository skillAssessmentRepository;

    @Inject
    SkillDefinitionRepository skillDefinitionRepository;

    public SkillLevels resolveForNodeIds(List<UUID> nodeIds) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            return SkillLevels.empty();
        }
        Map<UUID, List<SkillAssessment>> assessmentsByNode = skillAssessmentRepository.findByNodeIds(nodeIds);
        if (assessmentsByNode.isEmpty()) {
            return SkillLevels.empty();
        }
        Set<UUID> skillIds = assessmentsByNode.values().stream()
                .flatMap(List::stream)
                .map(a -> a.skillId)
                .collect(Collectors.toSet());
        if (skillIds.isEmpty()) {
            return SkillLevels.empty();
        }
        Map<UUID, String> namesById = skillDefinitionRepository.findByIds(new ArrayList<>(skillIds)).stream()
                .collect(Collectors.toMap(d -> d.id, d -> d.name));

        Map<UUID, Map<String, Integer>> levelsByNodeForResult = new HashMap<>();
        Map<UUID, Map<String, Short>> levelsByNodeForScoring = new HashMap<>();
        for (Map.Entry<UUID, List<SkillAssessment>> entry : assessmentsByNode.entrySet()) {
            Map<String, Integer> resultLevels = new LinkedHashMap<>();
            Map<String, Short> scoringLevels = new HashMap<>();
            for (SkillAssessment assessment : entry.getValue()) {
                if (assessment.level <= 0) {
                    continue;
                }
                String name = namesById.get(assessment.skillId);
                if (name == null) {
                    continue;
                }
                resultLevels.put(name, (int) assessment.level);
                scoringLevels.put(name.toLowerCase(Locale.ROOT).trim(), assessment.level);
            }
            if (!resultLevels.isEmpty()) {
                levelsByNodeForResult.put(entry.getKey(), resultLevels);
            }
            if (!scoringLevels.isEmpty()) {
                levelsByNodeForScoring.put(entry.getKey(), scoringLevels);
            }
        }
        return new SkillLevels(levelsByNodeForResult, levelsByNodeForScoring);
    }

    public record SkillLevels(
            Map<UUID, Map<String, Integer>> levelsByNodeForResult,
            Map<UUID, Map<String, Short>> levelsByNodeForScoring
    ) {
        public static SkillLevels empty() {
            return new SkillLevels(Collections.emptyMap(), Collections.emptyMap());
        }
    }
}
