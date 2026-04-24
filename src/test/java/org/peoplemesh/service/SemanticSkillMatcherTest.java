package org.peoplemesh.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.domain.model.SkillDefinition;
import org.peoplemesh.repository.SkillDefinitionRepository;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class SemanticSkillMatcherTest {

    @Mock
    EmbeddingService embeddingService;

    @Mock
    SkillDefinitionRepository skillDefinitionRepository;

    @InjectMocks
    SemanticSkillMatcher semanticSkillMatcher;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        lenient().when(skillDefinitionRepository.findAll()).thenReturn(List.of());
    }

    @Test
    void matchSkills_nullOrEmptyInputs_returnsEmpty() {
        assertTrue(semanticSkillMatcher.matchSkills(null, List.of("Java"), 0.7).isEmpty());
        assertTrue(semanticSkillMatcher.matchSkills(List.of("Java"), null, 0.7).isEmpty());
        assertTrue(semanticSkillMatcher.matchSkills(List.of(), List.of("Java"), 0.7).isEmpty());
        assertTrue(semanticSkillMatcher.matchSkills(List.of("Java"), List.of(), 0.7).isEmpty());
    }

    @Test
    void matchSkills_sanitizeRemovesDuplicatesAndBlanks_returnsEmptyWhenNothingUsable() {
        List<SemanticSkillMatcher.SemanticMatch> out = semanticSkillMatcher.matchSkills(
                List.of("  ", "\t"),
                List.of("  ", "\n"),
                0.7);

        assertTrue(out.isEmpty());
        verify(embeddingService, never()).generateEmbeddings(any());
    }

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
                .thenReturn(java.util.Collections.singletonList(
                        new SkillDefinitionRepository.SimilarSkillRow(id, "Kubernetes", List.of(), 0.92)));

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

    @Test
    void matchSkills_repositoryRowUsesCachedAliasesWhenRowAliasDoesNotMatch() {
        UUID id = UUID.randomUUID();
        when(embeddingService.generateEmbeddings(any())).thenReturn(List.of(new float[]{1f, 0f}));
        when(skillDefinitionRepository.findSimilarByEmbedding(any(), anyInt(), anyDouble()))
                .thenReturn(Collections.singletonList(
                        new SkillDefinitionRepository.SimilarSkillRow(id, "Unrelated", List.of(), 0.91)));

        SkillDefinition definition = new SkillDefinition();
        definition.id = id;
        definition.name = "Kubernetes";
        definition.aliases = List.of("k8s");
        definition.embedding = new float[]{1f, 0f};
        when(skillDefinitionRepository.findAll()).thenReturn(List.of(definition));

        List<SemanticSkillMatcher.SemanticMatch> matches = semanticSkillMatcher.matchSkills(
                List.of("container orchestration"),
                List.of("k8s"),
                0.7
        );

        assertEquals(1, matches.size());
        assertEquals("k8s", matches.get(0).matchedSkill());
    }

    @Test
    void matchSkills_withNullGeneratedEmbedding_skipsSemanticCandidate() {
        when(embeddingService.generateEmbeddings(any())).thenReturn(Arrays.asList((float[]) null));

        List<SemanticSkillMatcher.SemanticMatch> matches = semanticSkillMatcher.matchSkills(
                List.of("k8s"),
                List.of("Kubernetes"),
                0.7
        );

        assertTrue(matches.isEmpty());
    }

    @Test
    void privateHelpers_coverNormalizeKeyAndSanitizeAndCacheLoading() throws Exception {
        Method normalizeKey = SemanticSkillMatcher.class.getDeclaredMethod("normalizeCacheKey", String.class);
        normalizeKey.setAccessible(true);
        assertEquals("", normalizeKey.invoke(null, new Object[]{null}));
        assertEquals("java", normalizeKey.invoke(null, " Java "));

        Method sanitize = SemanticSkillMatcher.class.getDeclaredMethod("sanitize", List.class);
        sanitize.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<String> cleaned = (List<String>) sanitize.invoke(null, Arrays.asList(" Java ", "java", null, "Go"));
        assertEquals(List.of("Java", "Go"), cleaned);

        Method loadEmbeddings = SemanticSkillMatcher.class.getDeclaredMethod("loadQueryEmbeddings", List.class);
        loadEmbeddings.setAccessible(true);
        when(embeddingService.generateEmbeddings(List.of("Java"))).thenReturn(List.of(new float[]{1f, 2f}));

        @SuppressWarnings("unchecked")
        List<float[]> first = (List<float[]>) loadEmbeddings.invoke(semanticSkillMatcher, List.of("Java"));
        assertEquals(1, first.size());
        assertEquals(2, first.get(0).length);

        // second call should be served by cache without new embedding generation
        @SuppressWarnings("unchecked")
        List<float[]> second = (List<float[]>) loadEmbeddings.invoke(semanticSkillMatcher, List.of("Java"));
        assertEquals(1, second.size());
        verify(embeddingService).generateEmbeddings(List.of("Java"));
    }

    @Test
    void pruneQueryEmbeddingCache_removesExpiredEntries() throws Exception {
        Field cacheField = SemanticSkillMatcher.class.getDeclaredField("queryEmbeddingCache");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, Object> cache = (Map<String, Object>) cacheField.get(semanticSkillMatcher);

        Class<?> cachedEmbeddingClass = Class.forName("org.peoplemesh.service.SemanticSkillMatcher$CachedEmbedding");
        var ctor = cachedEmbeddingClass.getDeclaredConstructor(Instant.class, float[].class);
        ctor.setAccessible(true);
        Object expired = ctor.newInstance(Instant.now().minusSeconds(700), new float[]{1f});
        Object fresh = ctor.newInstance(Instant.now(), new float[]{2f});
        cache.put("expired", expired);
        cache.put("fresh", fresh);

        Method prune = SemanticSkillMatcher.class.getDeclaredMethod("pruneQueryEmbeddingCache", Instant.class);
        prune.setAccessible(true);
        prune.invoke(semanticSkillMatcher, Instant.now());

        assertFalse(cache.containsKey("expired"));
        assertTrue(cache.containsKey("fresh"));
    }

    @Test
    void privateMatchHelpers_coverNoCandidateAndBestSelection() throws Exception {
        Method findCandidateMatch = SemanticSkillMatcher.class.getDeclaredMethod(
                "findCandidateMatch", List.class, String.class, List.class);
        findCandidateMatch.setAccessible(true);
        assertNull(findCandidateMatch.invoke(semanticSkillMatcher, List.of("Go"), null, null));

        Method findExactMatch = SemanticSkillMatcher.class.getDeclaredMethod(
                "findExactMatch", String.class, List.class);
        findExactMatch.setAccessible(true);
        assertEquals("Java", findExactMatch.invoke(semanticSkillMatcher, "java", List.of("Java", "Go")));
    }
}
