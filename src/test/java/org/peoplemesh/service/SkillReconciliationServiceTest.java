package org.peoplemesh.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.domain.dto.SkillAssessmentDto;
import org.peoplemesh.domain.model.MeshNode;
import org.peoplemesh.domain.model.SkillDefinition;
import org.peoplemesh.util.VectorMath;
 
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkillReconciliationServiceTest {

    @Mock
    EmbeddingService embeddingService;

    @Test
    void reconcile_missingNode_returnsEmpty() {
        TestableSkillReconciliationService service = newService();
        service.node = null;

        List<SkillAssessmentDto> out = service.reconcile(UUID.randomUUID(), UUID.randomUUID());

        assertTrue(out.isEmpty());
    }

    @Test
    void reconcile_emptyTags_returnsEmpty() {
        TestableSkillReconciliationService service = newService();
        service.node = nodeWithTags(List.of());

        List<SkillAssessmentDto> out = service.reconcile(UUID.randomUUID(), UUID.randomUUID());

        assertTrue(out.isEmpty());
    }

    @Test
    void reconcile_emptyCatalog_returnsEmpty() {
        TestableSkillReconciliationService service = newService();
        service.node = nodeWithTags(List.of("java"));
        service.catalogSkills = List.of();

        List<SkillAssessmentDto> out = service.reconcile(UUID.randomUUID(), UUID.randomUUID());

        assertTrue(out.isEmpty());
    }

    @Test
    void reconcile_exactNameMatch_addsSuggestion() {
        TestableSkillReconciliationService service = newService();
        SkillDefinition javaSkill = skill("Java", "TECH", List.of("jdk"), null);
        service.node = nodeWithTags(List.of("java"));
        service.catalogSkills = List.of(javaSkill);

        List<SkillAssessmentDto> out = service.reconcile(UUID.randomUUID(), UUID.randomUUID());

        assertEquals(1, out.size());
        assertEquals("EXACT", out.getFirst().matchType());
        assertEquals(1.0, out.getFirst().confidence(), 1e-9);
        assertEquals(javaSkill.id, out.getFirst().skillId());
    }

    @Test
    void reconcile_aliasMatch_addsSuggestion() {
        TestableSkillReconciliationService service = newService();
        SkillDefinition javaSkill = skill("Java", "TECH", List.of("jdk"), null);
        service.node = nodeWithTags(List.of("JDK"));
        service.catalogSkills = List.of(javaSkill);

        List<SkillAssessmentDto> out = service.reconcile(UUID.randomUUID(), UUID.randomUUID());

        assertEquals(1, out.size());
        assertEquals("EXACT", out.getFirst().matchType());
        assertEquals(javaSkill.id, out.getFirst().skillId());
    }

    @Test
    void reconcile_existingExactMatch_isSkipped() {
        TestableSkillReconciliationService service = newService();
        SkillDefinition javaSkill = skill("Java", "TECH", List.of(), null);
        service.node = nodeWithTags(List.of("java"));
        service.catalogSkills = List.of(javaSkill);
        service.existingAssessmentIds = Set.of(javaSkill.id);

        List<SkillAssessmentDto> out = service.reconcile(UUID.randomUUID(), UUID.randomUUID());

        assertTrue(out.isEmpty());
    }

    @Test
    void reconcile_fuzzyMatchAboveThreshold_addsSuggestion() {
        TestableSkillReconciliationService service = newService();
        SkillDefinition javaSkill = skill("Java", "TECH", List.of(), new float[]{1f, 0f});
        SkillDefinition goSkill = skill("Go", "TECH", List.of(), new float[]{0f, 1f});
        service.node = nodeWithTags(List.of("java backend"));
        service.catalogSkills = List.of(javaSkill, goSkill);
        service.threshold = 0.70;
        when(embeddingService.generateEmbedding("java backend")).thenReturn(new float[]{1f, 0f});

        List<SkillAssessmentDto> out = service.reconcile(UUID.randomUUID(), UUID.randomUUID());

        assertEquals(1, out.size());
        assertEquals("FUZZY", out.getFirst().matchType());
        assertEquals(1.0, out.getFirst().confidence(), 1e-9);
        assertEquals(javaSkill.id, out.getFirst().skillId());
    }

    @Test
    void reconcile_fuzzyBelowThreshold_isSkipped() {
        TestableSkillReconciliationService service = newService();
        SkillDefinition javaSkill = skill("Java", "TECH", List.of(), new float[]{1f, 0f});
        service.node = nodeWithTags(List.of("java backend"));
        service.catalogSkills = List.of(javaSkill);
        service.threshold = 1.01;
        when(embeddingService.generateEmbedding("java backend")).thenReturn(new float[]{1f, 0f});

        List<SkillAssessmentDto> out = service.reconcile(UUID.randomUUID(), UUID.randomUUID());

        assertTrue(out.isEmpty());
    }

    @Test
    void reconcile_embeddingFailure_isIgnored() {
        TestableSkillReconciliationService service = newService();
        SkillDefinition javaSkill = skill("Java", "TECH", List.of(), new float[]{1f, 0f});
        service.node = nodeWithTags(List.of("java backend"));
        service.catalogSkills = List.of(javaSkill);
        when(embeddingService.generateEmbedding("java backend"))
                .thenThrow(new RuntimeException("embedding down"));

        List<SkillAssessmentDto> out = service.reconcile(UUID.randomUUID(), UUID.randomUUID());

        assertTrue(out.isEmpty());
    }

    @Test
    void reconcile_fuzzyExistingAssessment_isSkipped() {
        TestableSkillReconciliationService service = newService();
        SkillDefinition javaSkill = skill("Java", "TECH", List.of(), new float[]{1f, 0f});
        service.node = nodeWithTags(List.of("backend java"));
        service.catalogSkills = List.of(javaSkill);
        service.existingAssessmentIds = Set.of(javaSkill.id);
        when(embeddingService.generateEmbedding("backend java")).thenReturn(new float[]{1f, 0f});

        List<SkillAssessmentDto> out = service.reconcile(UUID.randomUUID(), UUID.randomUUID());

        assertTrue(out.isEmpty());
    }

    @Test
    void cosineSimilarity_delegatesToVectorMath() {
        float[] a = {1f, 0f};
        float[] b = {1f, 0f};
        assertEquals(1.0, VectorMath.cosineSimilarity(a, b), 1e-9);
    }

    private TestableSkillReconciliationService newService() {
        TestableSkillReconciliationService service = new TestableSkillReconciliationService();
        service.embeddingService = embeddingService;
        return service;
    }

    private static MeshNode nodeWithTags(List<String> tags) {
        MeshNode n = new MeshNode();
        n.tags = tags;
        return n;
    }

    private static SkillDefinition skill(String name, String category, List<String> aliases, float[] embedding) {
        SkillDefinition d = new SkillDefinition();
        d.id = UUID.randomUUID();
        d.name = name;
        d.category = category;
        d.aliases = aliases;
        d.embedding = embedding;
        return d;
    }

    private static final class TestableSkillReconciliationService extends SkillReconciliationService {
        MeshNode node;
        List<SkillDefinition> catalogSkills = List.of();
        Set<UUID> existingAssessmentIds = Set.of();
        double threshold = 0.75;

        @Override
        MeshNode loadNode(UUID nodeId) {
            return node;
        }

        @Override
        List<SkillDefinition> loadCatalogSkills(UUID catalogId) {
            return catalogSkills;
        }

        @Override
        Set<UUID> loadExistingAssessmentIds(UUID nodeId) {
            return existingAssessmentIds;
        }

        @Override
        double reconciliationThreshold() {
            return threshold;
        }
    }
}
