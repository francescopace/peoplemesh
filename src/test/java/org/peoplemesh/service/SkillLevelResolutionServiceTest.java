package org.peoplemesh.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.domain.model.SkillAssessment;
import org.peoplemesh.domain.model.SkillDefinition;
import org.peoplemesh.repository.SkillAssessmentRepository;
import org.peoplemesh.repository.SkillDefinitionRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SkillLevelResolutionServiceTest {

    @Mock
    SkillAssessmentRepository skillAssessmentRepository;

    @Mock
    SkillDefinitionRepository skillDefinitionRepository;

    @InjectMocks
    SkillLevelResolutionService service;

    @Test
    void resolveForNodeIds_returnsBothScoringAndResultMaps() {
        UUID nodeId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();

        SkillAssessment assessment = new SkillAssessment();
        assessment.nodeId = nodeId;
        assessment.skillId = skillId;
        assessment.level = 3;

        SkillDefinition definition = new SkillDefinition();
        definition.id = skillId;
        definition.name = "Java";

        when(skillAssessmentRepository.findByNodeIds(List.of(nodeId)))
                .thenReturn(Map.of(nodeId, List.of(assessment)));
        when(skillDefinitionRepository.findByIds(List.of(skillId)))
                .thenReturn(List.of(definition));

        SkillLevelResolutionService.SkillLevels result = service.resolveForNodeIds(List.of(nodeId));

        assertEquals(3, result.levelsByNodeForResult().get(nodeId).get("Java"));
        assertEquals(3, result.levelsByNodeForScoring().get(nodeId).get("java").intValue());
    }

    @Test
    void resolveForNodeIds_emptyInput_returnsEmpty() {
        SkillLevelResolutionService.SkillLevels result = service.resolveForNodeIds(List.of());
        assertTrue(result.levelsByNodeForResult().isEmpty());
        assertTrue(result.levelsByNodeForScoring().isEmpty());
    }
}
