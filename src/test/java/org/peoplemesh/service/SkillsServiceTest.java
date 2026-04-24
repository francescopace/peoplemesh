package org.peoplemesh.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.domain.dto.SkillDefinitionDto;
import org.peoplemesh.domain.exception.ForbiddenBusinessException;
import org.peoplemesh.domain.model.SkillDefinition;
import org.peoplemesh.repository.SkillDefinitionRepository;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SkillsServiceTest {

    @Mock
    SkillDefinitionRepository skillDefinitionRepository;
    @Mock
    EntitlementService entitlementService;
    @Mock
    EmbeddingService embeddingService;

    @InjectMocks
    SkillsService service;

    @Test
    void listSkills_withQuery_usesAliasSuggestion() {
        SkillDefinition java = skill("java", List.of("Java"), 10);
        when(skillDefinitionRepository.suggestByAlias("ja", 50)).thenReturn(List.of(java));

        List<SkillDefinitionDto> result = service.listSkills("ja", 0, 50);

        assertEquals(1, result.size());
        assertEquals("java", result.get(0).name());
    }

    @Test
    void importCsv_nonAdmin_throwsForbidden() {
        UUID userId = UUID.randomUUID();
        when(entitlementService.isAdmin(userId)).thenReturn(false);

        assertThrows(ForbiddenBusinessException.class,
                () -> service.importCsv(userId, new ByteArrayInputStream("name\nJava\n".getBytes())));
    }

    @Test
    void cleanupUnused_admin_deletesRows() {
        UUID userId = UUID.randomUUID();
        when(entitlementService.isAdmin(userId)).thenReturn(true);
        when(skillDefinitionRepository.deleteUnused()).thenReturn(4);

        int deleted = service.cleanupUnused(userId);
        assertEquals(4, deleted);
    }

    @Test
    void normalizeAndUpsertSkills_createsMissingAndUsesCanonicalNames() {
        when(skillDefinitionRepository.findByNameOrAlias("java")).thenReturn(java.util.Optional.of(skill("java", List.of("Java"), 2)));
        when(skillDefinitionRepository.findByNameOrAlias("golang")).thenReturn(java.util.Optional.empty());

        Set<String> normalized = service.normalizeAndUpsertSkills(List.of("Java", "GoLang"));

        assertEquals(Set.of("java", "golang"), normalized);
        verify(skillDefinitionRepository, times(1)).upsert(any(SkillDefinition.class));
    }

    private static SkillDefinition skill(String name, List<String> aliases, int usageCount) {
        SkillDefinition skill = new SkillDefinition();
        skill.id = UUID.randomUUID();
        skill.name = name;
        skill.aliases = aliases;
        skill.usageCount = usageCount;
        return skill;
    }
}
