package org.peoplemesh.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.domain.dto.SkillAssessmentDto;
import org.peoplemesh.domain.model.SkillAssessment;
import org.peoplemesh.domain.model.SkillDefinition;
import org.peoplemesh.repository.SkillAssessmentRepository;
import org.peoplemesh.repository.SkillDefinitionRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultSkillAssessmentServiceTest {

    @Mock
    SkillAssessmentRepository skillAssessmentRepository;

    @Mock
    SkillDefinitionRepository skillDefinitionRepository;

    @InjectMocks
    DefaultSkillAssessmentService service;

    @Test
    void listAssessments_whenNoAssessments_returnsEmpty() {
        UUID nodeId = UUID.randomUUID();
        when(skillAssessmentRepository.findByNode(nodeId)).thenReturn(List.of());

        List<SkillAssessmentDto> result = service.listAssessments(nodeId, null);

        assertTrue(result.isEmpty());
        verifyNoInteractions(skillDefinitionRepository);
    }

    @Test
    void listAssessments_filtersByCatalogAndMissingDefinitions() {
        UUID nodeId = UUID.randomUUID();
        UUID wantedCatalog = UUID.randomUUID();
        UUID otherCatalog = UUID.randomUUID();
        UUID skillA = UUID.randomUUID();
        UUID skillB = UUID.randomUUID();

        SkillAssessment first = assessment(nodeId, skillA, (short) 4, true);
        SkillAssessment second = assessment(nodeId, skillB, (short) 2, false);
        when(skillAssessmentRepository.findByNode(nodeId)).thenReturn(List.of(first, second));

        SkillDefinition defA = definition(skillA, wantedCatalog, "Java", "Backend");
        SkillDefinition defB = definition(skillB, otherCatalog, "Rust", "Systems");
        when(skillDefinitionRepository.findMapByIds(List.of(skillA, skillB)))
                .thenReturn(Map.of(skillA, defA, skillB, defB));

        List<SkillAssessmentDto> result = service.listAssessments(nodeId, wantedCatalog);

        assertEquals(1, result.size());
        assertEquals(skillA, result.get(0).skillId());
        assertEquals("Java", result.get(0).skillName());
    }

    @Test
    void loadNamedLevels_withNoPositiveLevels_returnsNull() {
        UUID nodeId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        when(skillAssessmentRepository.findByNode(nodeId))
                .thenReturn(List.of(assessment(nodeId, skillId, (short) 0, true)));

        assertNull(service.loadNamedLevels(nodeId));
    }

    @Test
    void loadNamedLevels_returnsMapForPositiveLevels() {
        UUID nodeId = UUID.randomUUID();
        UUID javaId = UUID.randomUUID();
        UUID pythonId = UUID.randomUUID();

        SkillAssessment java = assessment(nodeId, javaId, (short) 5, true);
        SkillAssessment python = assessment(nodeId, pythonId, (short) 3, true);
        when(skillAssessmentRepository.findByNode(nodeId)).thenReturn(List.of(java, python));

        SkillDefinition javaDef = definition(javaId, UUID.randomUUID(), "Java", "Backend");
        SkillDefinition pythonDef = definition(pythonId, UUID.randomUUID(), "Python", "Backend");
        when(skillDefinitionRepository.findByIds(List.of(javaId, pythonId)))
                .thenReturn(List.of(javaDef, pythonDef));

        Map<String, Integer> result = service.loadNamedLevels(nodeId);

        assertEquals(Map.of("Java", 5, "Python", 3), result);
        verify(skillDefinitionRepository).findByIds(List.of(javaId, pythonId));
    }

    private static SkillAssessment assessment(UUID nodeId, UUID skillId, short level, boolean interest) {
        SkillAssessment assessment = new SkillAssessment();
        assessment.nodeId = nodeId;
        assessment.skillId = skillId;
        assessment.level = level;
        assessment.interest = interest;
        assessment.source = "SELF";
        return assessment;
    }

    private static SkillDefinition definition(UUID skillId, UUID catalogId, String name, String category) {
        SkillDefinition definition = new SkillDefinition();
        definition.id = skillId;
        definition.catalogId = catalogId;
        definition.name = name;
        definition.category = category;
        return definition;
    }
}
