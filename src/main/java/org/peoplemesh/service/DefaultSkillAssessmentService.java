package org.peoplemesh.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.peoplemesh.domain.dto.SkillAssessmentDto;
import org.peoplemesh.domain.model.SkillAssessment;
import org.peoplemesh.domain.model.SkillDefinition;
import org.peoplemesh.repository.SkillAssessmentRepository;
import org.peoplemesh.repository.SkillDefinitionRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class DefaultSkillAssessmentService implements SkillAssessmentService {

    @Inject
    SkillAssessmentRepository skillAssessmentRepository;

    @Inject
    SkillDefinitionRepository skillDefinitionRepository;

    @Override
    public List<SkillAssessmentDto> listAssessments(UUID nodeId, UUID catalogId) {
        List<SkillAssessment> assessments = skillAssessmentRepository.findByNode(nodeId);
        if (assessments.isEmpty()) {
            return List.of();
        }
        List<UUID> skillIds = assessments.stream().map(a -> a.skillId).distinct().toList();
        Map<UUID, SkillDefinition> definitions = skillDefinitionRepository.findMapByIds(skillIds);
        List<SkillAssessmentDto> result = new ArrayList<>();
        for (SkillAssessment assessment : assessments) {
            SkillDefinition definition = definitions.get(assessment.skillId);
            if (definition == null) {
                continue;
            }
            if (catalogId != null && !catalogId.equals(definition.catalogId)) {
                continue;
            }
            result.add(SkillAssessmentDto.fromAssessment(
                    assessment.skillId,
                    definition.name,
                    definition.category,
                    assessment.level,
                    assessment.interest,
                    assessment.source));
        }
        return result;
    }

    @Override
    public Map<String, Integer> loadNamedLevels(UUID nodeId) {
        List<SkillAssessment> assessments = skillAssessmentRepository.findByNode(nodeId);
        if (assessments.isEmpty()) {
            return null;
        }
        List<UUID> skillIds = assessments.stream()
                .filter(a -> a.level > 0)
                .map(a -> a.skillId)
                .toList();
        if (skillIds.isEmpty()) {
            return null;
        }
        Map<UUID, String> definitionNames = skillDefinitionRepository.findByIds(skillIds).stream()
                .collect(Collectors.toMap(d -> d.id, d -> d.name));
        Map<String, Integer> levels = new LinkedHashMap<>();
        for (SkillAssessment assessment : assessments) {
            if (assessment.level <= 0) {
                continue;
            }
            String name = definitionNames.get(assessment.skillId);
            if (name != null) {
                levels.put(name, (int) assessment.level);
            }
        }
        return levels.isEmpty() ? null : levels;
    }
}
