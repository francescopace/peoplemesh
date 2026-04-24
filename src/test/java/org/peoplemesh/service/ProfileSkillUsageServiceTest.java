package org.peoplemesh.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.model.MeshNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileSkillUsageServiceTest {

    @Mock
    SkillsService skillsService;

    private ProfileSkillUsageService service;

    @BeforeEach
    void setUp() {
        service = new ProfileSkillUsageService();
        service.skillsService = skillsService;
    }

    @Test
    void collectProfileSkills_includesTagsSoftAndTools() {
        MeshNode node = userNode();
        node.tags = List.of("Java");
        node.structuredData.put("skills_soft", List.of("Mentoring"));
        node.structuredData.put("tools_and_tech", List.of("Docker"));
        when(skillsService.normalizeSkills(List.of("Java", "Mentoring", "Docker")))
                .thenReturn(Set.of("java", "mentoring", "docker"));

        Set<String> normalized = service.collectProfileSkills(node);

        assertEquals(Set.of("java", "mentoring", "docker"), normalized);
    }

    @Test
    void normalizeSkillFields_canonicalizesAndWritesBackStructuredData() {
        MeshNode node = userNode();
        node.tags = List.of("Java");
        node.structuredData.put("skills_soft", List.of("Mentoring"));
        node.structuredData.put("tools_and_tech", List.of("Docker"));

        when(skillsService.canonicalizeSkillList(List.of("Java"), true)).thenReturn(List.of("java"));
        when(skillsService.canonicalizeSkillList(List.of("Mentoring"), true)).thenReturn(List.of("mentoring"));
        when(skillsService.canonicalizeSkillList(List.of("Docker"), true)).thenReturn(List.of("docker"));

        Set<String> normalized = service.normalizeSkillFields(node);

        assertEquals(List.of("java"), node.tags);
        assertEquals(List.of("mentoring"), node.structuredData.get("skills_soft"));
        assertEquals(List.of("docker"), node.structuredData.get("tools_and_tech"));
        assertEquals(Set.of("java", "mentoring", "docker"), normalized);
    }

    @Test
    void syncUsageCounters_updatesAddedAndRemovedSkills() {
        service.syncUsageCounters(Set.of("java", "docker"), Set.of("java", "kubernetes"));

        verify(skillsService).decrementUsage(Set.of("docker"));
        verify(skillsService).incrementUsage(Set.of("kubernetes"));
    }

    private static MeshNode userNode() {
        MeshNode node = new MeshNode();
        node.id = UUID.randomUUID();
        node.nodeType = NodeType.USER;
        node.structuredData = new LinkedHashMap<>();
        return node;
    }
}
