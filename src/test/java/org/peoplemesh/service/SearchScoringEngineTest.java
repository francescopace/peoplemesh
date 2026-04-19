package org.peoplemesh.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.domain.dto.SearchQuery;
import org.peoplemesh.domain.dto.SkillWithLevel;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.enums.WorkMode;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SearchScoringEngineTest {

    private SearchScoringEngine engine;
    private SemanticSkillMatcher semanticSkillMatcher;

    @BeforeEach
    void setUp() throws Exception {
        AppConfig config = mock(AppConfig.class);
        AppConfig.SearchConfig searchConfig = mock(AppConfig.SearchConfig.class);
        when(config.search()).thenReturn(searchConfig);
        when(searchConfig.skillMatchThreshold()).thenReturn(0.7);

        semanticSkillMatcher = mock(SemanticSkillMatcher.class);
        when(semanticSkillMatcher.matchSkills(anyList(), anyList(), anyDouble())).thenReturn(Collections.emptyList());

        engine = new SearchScoringEngine();
        setField(engine, "config", config);
        setField(engine, "semanticSkillMatcher", semanticSkillMatcher);
    }

    @Test
    void scoreAndRank_userCandidate_combinesWeightedSignals() {
        SearchQuery query = new SearchQuery(
                new SearchQuery.MustHaveFilters(List.of("Java"), null, List.of(), List.of("English"), List.of(), List.of()),
                new SearchQuery.NiceToHaveFilters(List.of("Docker"), null, List.of(), List.of()),
                "unknown",
                null,
                List.of("backend"),
                "java docker",
                "unknown");

        SearchService.RawNodeCandidate candidate = userCandidate(
                UUID.randomUUID(),
                0.9,
                List.of("Java"),
                Map.of("languages_spoken", List.of("English"), "tools_and_tech", List.of("Docker")));

        when(semanticSkillMatcher.matchSkills(List.of("Java"), List.of("Java", "Docker"), 0.7))
                .thenReturn(List.of(new SemanticSkillMatcher.SemanticMatch("Java", "Java", 0.95)));
        when(semanticSkillMatcher.matchSkills(List.of("Docker"), List.of("Java", "Docker"), 0.7))
                .thenReturn(List.of(new SemanticSkillMatcher.SemanticMatch("Docker", "Docker", 0.91)));

        List<SearchService.ScoredCandidate> scored = engine.scoreAndRank(
                List.of(candidate), query, Collections.emptyMap(), new SearchService.MatchContext("US", WorkMode.REMOTE, null));

        assertEquals(1, scored.size());
        assertTrue(scored.get(0).score() > 0.85);
        assertTrue(scored.get(0).breakdown().reasonCodes().contains("MUST_HAVE_SKILLS"));
        assertTrue(scored.get(0).breakdown().reasonCodes().contains("NICE_TO_HAVE_SKILLS"));
    }

    @Test
    void scoreAndRank_genericNode_emitsNodeReasonCode() {
        SearchQuery query = new SearchQuery(
                new SearchQuery.MustHaveFilters(List.of("Kafka"), null, List.of(), List.of(), List.of(), List.of()),
                new SearchQuery.NiceToHaveFilters(List.of("Java"), null, List.of(), List.of()),
                "unknown",
                null,
                List.of("event"),
                "kafka stream",
                "unknown");

        SearchService.RawNodeCandidate candidate = new SearchService.RawNodeCandidate(
                UUID.randomUUID(), NodeType.PROJECT, "Event project", "Kafka event stream",
                List.of("Kafka", "Streams"), "US", Instant.now(), null, 0.82);

        when(semanticSkillMatcher.matchSkills(List.of("Kafka"), List.of("Kafka", "Streams"), 0.7))
                .thenReturn(List.of(new SemanticSkillMatcher.SemanticMatch("Kafka", "Kafka", 0.96)));
        when(semanticSkillMatcher.matchSkills(List.of("Java"), List.of("Kafka", "Streams"), 0.7))
                .thenReturn(List.of());

        List<SearchService.ScoredCandidate> scored = engine.scoreAndRank(
                List.of(candidate), query, Collections.emptyMap(), new SearchService.MatchContext("US", WorkMode.REMOTE, null));

        assertEquals(1, scored.size());
        assertTrue(scored.get(0).score() > 0);
        assertTrue(scored.get(0).breakdown().reasonCodes().contains("NODE_PROJECT"));
    }

    @Test
    void scoreAndRank_withTargetSeniority_reranksMatchingCandidateAbove() {
        SearchQuery query = new SearchQuery(null, null, "SENIOR", null, List.of(), "query", "unknown");

        SearchService.RawNodeCandidate senior = userCandidate(
                UUID.randomUUID(),
                0.8,
                List.of("Java"),
                Map.of("seniority", "SENIOR"));
        SearchService.RawNodeCandidate mid = userCandidate(
                UUID.randomUUID(),
                0.8,
                List.of("Java"),
                Map.of("seniority", "MID"));

        List<SearchService.ScoredCandidate> scored = engine.scoreAndRank(
                List.of(mid, senior), query, Collections.emptyMap(), SearchService.MatchContext.empty());

        assertEquals(2, scored.size());
        assertEquals(senior.nodeId(), scored.get(0).node().nodeId());
    }

    @Test
    void scoreAndRank_withNegativeSkills_penalizesMatchingCandidate() {
        SearchQuery query = new SearchQuery(
                null,
                null,
                "unknown",
                new SearchQuery.NegativeFilters(null, List.of("Cobol"), List.of()),
                List.of(),
                "query",
                "unknown");

        SearchService.RawNodeCandidate clean = new SearchService.RawNodeCandidate(
                UUID.randomUUID(), NodeType.PROJECT, "Modern", "Java backend",
                List.of("Java"), "US", Instant.now(), null, 0.8);
        SearchService.RawNodeCandidate penalized = new SearchService.RawNodeCandidate(
                UUID.randomUUID(), NodeType.PROJECT, "Legacy", "Cobol maintenance",
                List.of("Cobol"), "US", Instant.now(), null, 0.8);

        List<SearchService.ScoredCandidate> scored = engine.scoreAndRank(
                List.of(clean, penalized), query, Collections.emptyMap(), SearchService.MatchContext.empty());

        assertEquals(2, scored.size());
        assertEquals(clean.nodeId(), scored.get(0).node().nodeId());
        assertTrue(scored.get(0).score() > scored.get(1).score());
    }

    @Test
    void scoreAndRank_levelAwareMustHave_usesCachedSkillLevels() {
        SearchQuery query = new SearchQuery(
                new SearchQuery.MustHaveFilters(null, List.of(new SkillWithLevel("Java", 3)), null, null, null, null),
                null,
                "unknown",
                null,
                List.of(),
                "java",
                "unknown");

        UUID highId = UUID.randomUUID();
        UUID lowId = UUID.randomUUID();
        SearchService.RawNodeCandidate high = userCandidate(highId, 0.7, List.of(), Map.of());
        SearchService.RawNodeCandidate low = userCandidate(lowId, 0.7, List.of(), Map.of());

        Map<UUID, Map<String, Short>> levelCache = Map.of(
                highId, Map.of("java", (short) 4),
                lowId, Map.of("java", (short) 1));

        List<SearchService.ScoredCandidate> scored = engine.scoreAndRank(
                List.of(low, high), query, levelCache, SearchService.MatchContext.empty());

        assertEquals(2, scored.size());
        assertEquals(highId, scored.get(0).node().nodeId());
        assertTrue(scored.get(0).score() > scored.get(1).score());
    }

    private static SearchService.RawNodeCandidate userCandidate(
            UUID nodeId,
            double cosine,
            List<String> tags,
            Map<String, Object> structuredData) {
        return new SearchService.RawNodeCandidate(
                nodeId,
                NodeType.USER,
                "User",
                "Engineer",
                tags,
                "US",
                Instant.now(),
                new LinkedHashMap<>(structuredData),
                cosine);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
