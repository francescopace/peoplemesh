package org.peoplemesh.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.domain.dto.SearchQuery;
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
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SearchScoringEngineTest {

    private SearchScoringEngine engine;
    private SemanticSkillMatcher semanticSkillMatcher;

    @BeforeEach
    void setUp() throws Exception {
        AppConfig config = mock(AppConfig.class);
        AppConfig.SkillsConfig skillsConfig = mock(AppConfig.SkillsConfig.class);
        when(config.skills()).thenReturn(skillsConfig);
        when(skillsConfig.matchThreshold()).thenReturn(0.8);

        semanticSkillMatcher = mock(SemanticSkillMatcher.class);
        when(semanticSkillMatcher.resolveSkillNeighbors(anyList(), anyDouble())).thenReturn(Map.of());
        when(semanticSkillMatcher.matchSkillsWithResolvedOptions(anyList(), anyList(), anyMap()))
                .thenReturn(Collections.emptyList());

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

        RawNodeCandidate candidate = userCandidate(
                UUID.randomUUID(),
                0.9,
                List.of("Java"),
                Map.of("languages_spoken", List.of("English"), "tools_and_tech", List.of("Docker")));

        when(semanticSkillMatcher.matchSkillsWithResolvedOptions(List.of("Java"), List.of("Java", "Docker"), Map.of()))
                .thenReturn(List.of(new SemanticSkillMatcher.SemanticMatch("Java", "Java", 0.95)));
        when(semanticSkillMatcher.matchSkillsWithResolvedOptions(List.of("Docker"), List.of("Java", "Docker"), Map.of()))
                .thenReturn(List.of(new SemanticSkillMatcher.SemanticMatch("Docker", "Docker", 0.91)));

        List<ScoredCandidate> scored = engine.scoreAndRank(
                List.of(candidate), query, new SearchService.MatchContext("US", WorkMode.REMOTE, null));

        assertEquals(1, scored.size());
        assertTrue(scored.get(0).score() >= 0.85);
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

        RawNodeCandidate candidate = new RawNodeCandidate(
                UUID.randomUUID(), NodeType.PROJECT, "Event project", "Kafka event stream",
                List.of("Kafka", "Streams"), "US", Instant.now(), null, 0.82);

        when(semanticSkillMatcher.matchSkillsWithResolvedOptions(List.of("Kafka"), List.of("Kafka", "Streams"), Map.of()))
                .thenReturn(List.of(new SemanticSkillMatcher.SemanticMatch("Kafka", "Kafka", 0.96)));
        when(semanticSkillMatcher.matchSkillsWithResolvedOptions(List.of("Java"), List.of("Kafka", "Streams"), Map.of()))
                .thenReturn(List.of());

        List<ScoredCandidate> scored = engine.scoreAndRank(
                List.of(candidate), query, new SearchService.MatchContext("US", WorkMode.REMOTE, null));

        assertEquals(1, scored.size());
        assertTrue(scored.get(0).score() > 0);
        assertTrue(scored.get(0).breakdown().reasonCodes().contains("NODE_PROJECT"));
        assertTrue(scored.get(0).breakdown().keywordScore() > 0);
        assertEquals(0.2, scored.get(0).breakdown().weightKeyword());
    }

    @Test
    void scoreAndRank_withTargetSeniority_boostsMatchingCandidateAndEmitsReasonCodes() {
        SearchQuery query = new SearchQuery(null, null, "SENIOR", null, List.of(), "query", "unknown");

        RawNodeCandidate senior = userCandidate(
                UUID.randomUUID(),
                0.8,
                List.of("Java"),
                Map.of("seniority", "SENIOR"));
        RawNodeCandidate mid = userCandidate(
                UUID.randomUUID(),
                0.8,
                List.of("Java"),
                Map.of("seniority", "MID"));

        List<ScoredCandidate> scored = engine.scoreAndRank(
                List.of(mid, senior), query, SearchService.MatchContext.empty());

        assertEquals(2, scored.size());
        ScoredCandidate boosted = scored.stream()
                .filter(sc -> sc.node().nodeId().equals(senior.nodeId()))
                .findFirst()
                .orElseThrow();
        ScoredCandidate penalized = scored.stream()
                .filter(sc -> sc.node().nodeId().equals(mid.nodeId()))
                .findFirst()
                .orElseThrow();
        assertTrue(boosted.breakdown().reasonCodes().contains("SENIORITY_MATCH"));
        assertTrue(penalized.breakdown().reasonCodes().contains("SENIORITY_MISMATCH"));
        assertTrue(boosted.score() > penalized.score());
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

        RawNodeCandidate clean = new RawNodeCandidate(
                UUID.randomUUID(), NodeType.PROJECT, "Modern", "Java backend",
                List.of("Java"), "US", Instant.now(), null, 0.8);
        RawNodeCandidate penalized = new RawNodeCandidate(
                UUID.randomUUID(), NodeType.PROJECT, "Legacy", "Cobol maintenance",
                List.of("Cobol"), "US", Instant.now(), null, 0.8);

        List<ScoredCandidate> scored = engine.scoreAndRank(
                List.of(clean, penalized), query, SearchService.MatchContext.empty());

        assertEquals(2, scored.size());
        assertEquals(clean.nodeId(), scored.get(0).node().nodeId());
        assertTrue(scored.get(0).score() > scored.get(1).score());
    }

    @Test
    void scoreAndRank_userCandidate_coversNullTagsLanguageIndustryAndNiceLevelBranches() {
        SearchQuery query = new SearchQuery(
                new SearchQuery.MustHaveFilters(List.of("Java"), null, List.of("Fintech"), List.of("German"), List.of(), List.of()),
                new SearchQuery.NiceToHaveFilters(List.of("Kubernetes"), null, List.of("Fintech"), List.of()),
                "unknown",
                new SearchQuery.NegativeFilters(null, List.of("Cobol"), List.of()),
                null,
                "query",
                "unknown");

        UUID nodeId = UUID.randomUUID();
        RawNodeCandidate candidate = userCandidate(
                nodeId,
                0.8,
                null,
                Map.of(
                        "tools_and_tech", List.of("Kubernetes"),
                        "skills_soft", List.of("Communication"),
                        "languages_spoken", List.of("Italian"),
                        "industries", List.of("Fintech")
                ));

        when(semanticSkillMatcher.matchSkillsWithResolvedOptions(List.of("Java"), List.of("Kubernetes", "Communication"), Map.of()))
                .thenReturn(List.of());
        when(semanticSkillMatcher.matchSkillsWithResolvedOptions(List.of(), List.of("Kubernetes", "Communication"), Map.of()))
                .thenReturn(List.of());

        List<ScoredCandidate> scored = engine.scoreAndRank(
                List.of(candidate),
                query,
                SearchService.MatchContext.empty());

        assertEquals(1, scored.size());
        assertTrue(scored.get(0).breakdown().reasonCodes().contains("INDUSTRY_MATCH"));
        assertEquals(0.0, scored.get(0).breakdown().languageScore());
        assertEquals(1.0, scored.get(0).breakdown().industryScore());
    }

    @Test
    void scoreAndRank_genericNode_withNullTagsUsesEmptyTagList() {
        SearchQuery query = new SearchQuery(
                null,
                new SearchQuery.NiceToHaveFilters(List.of("Java"), null, List.of(), List.of()),
                "unknown",
                null,
                List.of("backend"),
                "query",
                "unknown");

        RawNodeCandidate candidate = new RawNodeCandidate(
                UUID.randomUUID(), NodeType.PROJECT, "Untyped", "Backend project",
                null, "US", Instant.now(), null, 0.5);

        List<ScoredCandidate> scored = engine.scoreAndRank(
                List.of(candidate), query, SearchService.MatchContext.empty());

        assertEquals(1, scored.size());
        assertTrue(scored.get(0).breakdown().reasonCodes().contains("NODE_PROJECT"));
    }

    @Test
    void scoreAndRank_seniorityWeightSkipsNonUserCandidates() {
        SearchQuery seniorityQuery = new SearchQuery(null, null, "SENIOR", null, List.of(), "query", "unknown");
        SearchQuery baselineQuery = new SearchQuery(null, null, "unknown", null, List.of(), "query", "unknown");
        RawNodeCandidate user = userCandidate(UUID.randomUUID(), 0.7, List.of("Java"), Map.of("seniority", "MID"));
        RawNodeCandidate project = new RawNodeCandidate(
                UUID.randomUUID(), NodeType.PROJECT, "Proj", "Desc", List.of("Java"), "US", Instant.now(), null, 0.71);

        List<ScoredCandidate> baseline = engine.scoreAndRank(
                List.of(project, user), baselineQuery, SearchService.MatchContext.empty());
        List<ScoredCandidate> reranked = engine.scoreAndRank(
                List.of(project, user), seniorityQuery, SearchService.MatchContext.empty());

        double baselineProjectScore = baseline.stream()
                .filter(sc -> sc.node().nodeType() == NodeType.PROJECT)
                .findFirst()
                .orElseThrow()
                .score();
        double rerankedProjectScore = reranked.stream()
                .filter(sc -> sc.node().nodeType() == NodeType.PROJECT)
                .findFirst()
                .orElseThrow()
                .score();

        assertEquals(2, reranked.size());
        assertEquals(baselineProjectScore, rerankedProjectScore);
    }

    private static RawNodeCandidate userCandidate(
            UUID nodeId,
            double cosine,
            List<String> tags,
            Map<String, Object> structuredData) {
        return new RawNodeCandidate(
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
