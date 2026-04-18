package org.peoplemesh.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.domain.model.SkillDefinition;
import org.peoplemesh.repository.SkillDefinitionRepository;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SemanticSkillMatcherTest {

    @Mock
    EmbeddingService embeddingService;

    @Mock
    SkillDefinitionRepository skillDefinitionRepository;

    @InjectMocks
    SemanticSkillMatcher semanticSkillMatcher;

    @Test
    void matchSkills_exactMatch_doesNotCallSemanticLookup() {
        List<SemanticSkillMatcher.SemanticMatch> matches = semanticSkillMatcher.matchSkills(
                List.of("Java"),
                List.of("Java", "Spring"),
                0.7
        );

        assertEquals(1, matches.size());
        assertEquals("Java", matches.get(0).querySkill());
        assertEquals("Java", matches.get(0).matchedSkill());
        verify(skillDefinitionRepository, never()).findSimilarByEmbedding(any(), anyInt(), anyDouble());
        verify(embeddingService, never()).generateEmbeddings(any());
    }

    @Test
    void matchSkills_semanticMatch_usesRepositoryNearestNeighbor() {
        UUID id = UUID.randomUUID();
        when(embeddingService.generateEmbeddings(any())).thenReturn(List.of(new float[]{1f, 0f}));
        when(skillDefinitionRepository.findSimilarByEmbedding(any(), anyInt(), anyDouble()))
                .thenReturn(java.util.Collections.singletonList(new Object[]{id, "Kubernetes", null, 0.92}));

        List<SemanticSkillMatcher.SemanticMatch> matches = semanticSkillMatcher.matchSkills(
                List.of("k8s"),
                List.of("Kubernetes", "Docker"),
                0.7
        );

        assertEquals(1, matches.size());
        assertEquals("k8s", matches.get(0).querySkill());
        assertEquals("Kubernetes", matches.get(0).matchedSkill());
        assertTrue(matches.get(0).similarity() >= 0.9);
    }

    @Test
    void matchSkills_whenRepositoryReturnsNothing_fallsBackToCacheEmbeddings() {
        when(embeddingService.generateEmbeddings(any())).thenReturn(List.of(new float[]{1f, 0f}));
        when(skillDefinitionRepository.findSimilarByEmbedding(any(), anyInt(), anyDouble()))
                .thenReturn(List.of());

        SkillDefinition definition = new SkillDefinition();
        definition.id = UUID.randomUUID();
        definition.name = "Kubernetes";
        definition.aliases = List.of("k8s");
        definition.embedding = new float[]{1f, 0f};
        when(skillDefinitionRepository.findAll()).thenReturn(List.of(definition));

        List<SemanticSkillMatcher.SemanticMatch> first = semanticSkillMatcher.matchSkills(
                List.of("container orchestration"),
                List.of("Kubernetes"),
                0.7
        );
        List<SemanticSkillMatcher.SemanticMatch> second = semanticSkillMatcher.matchSkills(
                List.of("container platform"),
                List.of("Kubernetes"),
                0.7
        );

        assertEquals(1, first.size());
        assertEquals(1, second.size());
        verify(skillDefinitionRepository).findAll();
    }
}
