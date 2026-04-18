package org.peoplemesh.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.MockedStatic;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.domain.dto.SearchQuery;
import org.peoplemesh.domain.dto.SearchResponse;
import org.peoplemesh.domain.model.SkillAssessment;

import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock EmbeddingService embeddingService;
    @Mock ConsentService consentService;
    @Mock org.peoplemesh.repository.MeshNodeSearchRepository searchRepository;
    @Mock org.peoplemesh.repository.SkillAssessmentRepository skillAssessmentRepository;
    @Mock org.peoplemesh.repository.SkillDefinitionRepository skillDefinitionRepository;
    @Mock AppConfig config;
    @Mock ObjectMapper objectMapper;
    @Mock AppConfig.SearchConfig searchConfig;
    @Mock SemanticSkillMatcher semanticSkillMatcher;

    @InjectMocks
    SearchService searchService;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(config.search()).thenReturn(searchConfig);
        lenient().when(searchConfig.minScore()).thenReturn(0.0);
        lenient().when(searchConfig.skillMatchThreshold()).thenReturn(0.7);
        lenient().when(skillAssessmentRepository.findByNodeIds(anyList())).thenReturn(Collections.emptyMap());
        lenient().when(skillDefinitionRepository.findByIds(anyList())).thenReturn(Collections.emptyList());
        lenient().when(semanticSkillMatcher.matchSkills(anyList(), anyList(), anyDouble())).thenAnswer(invocation -> {
            List<String> querySkills = invocation.getArgument(0);
            List<String> candidateSkills = invocation.getArgument(1);
            List<SemanticSkillMatcher.SemanticMatch> matches = new ArrayList<>();
            if (querySkills == null || candidateSkills == null) {
                return matches;
            }
            for (String querySkill : querySkills) {
                if (querySkill == null) {
                    continue;
                }
                for (String candidateSkill : candidateSkills) {
                    if (candidateSkill != null && MatchingUtils.termsMatch(querySkill, candidateSkill)) {
                        matches.add(new SemanticSkillMatcher.SemanticMatch(querySkill, candidateSkill, 1.0));
                        break;
                    }
                }
            }
            return matches;
        });

        Field parsersField = SearchService.class.getDeclaredField("queryParsers");
        parsersField.setAccessible(true);
        parsersField.set(searchService, Collections.emptyList());
    }

    @Test
    void search_noConsent_returnsEmptyResponse() {
        when(consentService.hasActiveConsent(userId, "professional_matching")).thenReturn(false);

        SearchResponse response = searchService.search(userId, "java developer");

        assertNotNull(response);
        assertTrue(response.results().isEmpty());
        verifyNoInteractions(embeddingService);
    }

    @Test
    void search_nullEmbedding_returnsEmptyResponse() {
        when(consentService.hasActiveConsent(userId, "professional_matching")).thenReturn(true);
        when(embeddingService.generateEmbedding(anyString())).thenReturn(null);

        SearchResponse response = searchService.search(userId, "some query");

        assertNotNull(response.parsedQuery());
        assertTrue(response.results().isEmpty());
    }

    @Test
    void search_withEmbedding_executesQuery() {
        when(consentService.hasActiveConsent(userId, "professional_matching")).thenReturn(true);
        when(embeddingService.generateEmbedding(anyString())).thenReturn(new float[]{0.1f, 0.2f});

        when(searchRepository.unifiedVectorSearch(any(float[].class), eq(userId),
                any(), any(), anyInt())).thenReturn(Collections.emptyList());

        SearchResponse response = searchService.search(userId, "java");

        assertNotNull(response);
        assertTrue(response.results().isEmpty());
        verify(searchRepository).unifiedVectorSearch(any(float[].class), eq(userId),
                any(), any(), anyInt());
    }

    @Test
    void search_withLocationInParsedQuery_addsSqlCountryParameter() throws Exception {
        SearchQueryParser parser = mock(SearchQueryParser.class);
        Field parsersField = SearchService.class.getDeclaredField("queryParsers");
        parsersField.setAccessible(true);
        parsersField.set(searchService, List.of(parser));
        SearchQuery parsed = new SearchQuery(
                new SearchQuery.MustHaveFilters(List.of(), null, List.of(), List.of(), List.of("Italy"), List.of()),
                null, "unknown", null, List.of(), "query", null);
        when(parser.parse("query")).thenReturn(Optional.of(parsed));

        when(consentService.hasActiveConsent(userId, "professional_matching")).thenReturn(true);
        when(embeddingService.generateEmbedding(anyString())).thenReturn(new float[]{0.1f});

        when(searchRepository.unifiedVectorSearch(any(float[].class), eq(userId),
                eq("IT"), any(), anyInt())).thenReturn(Collections.emptyList());

        searchService.search(userId, "query");

        verify(searchRepository).unifiedVectorSearch(any(float[].class), eq(userId),
                eq("IT"), any(), anyInt());
    }

    @Test
    void search_withParser_usesParserResult() throws Exception {
        SearchQueryParser parser = mock(SearchQueryParser.class);
        Field parsersField = SearchService.class.getDeclaredField("queryParsers");
        parsersField.setAccessible(true);
        parsersField.set(searchService, List.of(parser));

        SearchQuery parsed = new SearchQuery(null, null, "unknown", null,
                List.of("java"), "java developer", "jobs");
        when(parser.parse("java developer")).thenReturn(Optional.of(parsed));

        when(consentService.hasActiveConsent(userId, "professional_matching")).thenReturn(true);
        when(embeddingService.generateEmbedding("java developer")).thenReturn(new float[]{0.5f});

        when(searchRepository.unifiedVectorSearch(any(float[].class), eq(userId),
                any(), any(), anyInt())).thenReturn(Collections.emptyList());

        SearchResponse response = searchService.search(userId, "java developer");

        verify(parser).parse("java developer");
        assertNotNull(response);
        assertNotNull(response.parsedQuery());
        assertEquals("jobs", response.parsedQuery().resultScope());
    }

    @Test
    void search_fallbackParse_filtersStopWords() {
        when(consentService.hasActiveConsent(userId, "professional_matching")).thenReturn(true);
        when(embeddingService.generateEmbedding(anyString())).thenReturn(null);

        SearchResponse response = searchService.search(userId, "looking for a java developer");

        assertNotNull(response.parsedQuery());
        assertFalse(response.parsedQuery().keywords().contains("looking"));
        assertFalse(response.parsedQuery().keywords().contains("for"));
        assertFalse(response.parsedQuery().keywords().contains("a"));
        assertTrue(response.parsedQuery().keywords().contains("java"));
        assertTrue(response.parsedQuery().mustHave().roles().contains("developer"));
        assertFalse(response.parsedQuery().mustHave().skills().contains("developer"));
    }

    @Test
    void search_withResults_scoresAndReturnsItems() throws Exception {
        when(consentService.hasActiveConsent(userId, "professional_matching")).thenReturn(true);
        when(embeddingService.generateEmbedding(anyString())).thenReturn(new float[]{0.5f, 0.5f});

        UUID nodeId = UUID.randomUUID();
        Object[] userRow = new Object[]{
                nodeId, "USER", "Alice Engineer", "Backend Developer",
                new String[]{"Java", "Python"}, "US",
                Timestamp.from(Instant.now()),
                "{\"languages_spoken\":[\"English\"],\"city\":\"NYC\",\"email\":\"alice@test.com\",\"linkedin_url\":\"https://linkedin.com/in/alice\"}",
                0.85
        };
        when(objectMapper.readValue(
                eq((String) userRow[7]),
                org.mockito.ArgumentMatchers.<com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>>any()))
                .thenReturn(new LinkedHashMap<>(Map.of(
                        "languages_spoken", List.of("English"),
                        "city", "NYC",
                        "email", "alice@test.com",
                        "linkedin_url", "https://linkedin.com/in/alice"
                )));
        when(searchRepository.unifiedVectorSearch(any(float[].class), eq(userId),
                any(), any(), anyInt())).thenReturn(rowList(userRow));

        try (MockedStatic<SkillAssessment> sa = mockStatic(SkillAssessment.class)) {
            sa.when(() -> SkillAssessment.findByNode(any())).thenReturn(Collections.emptyList());

            SearchResponse response = searchService.search(userId, "java developer");

            assertNotNull(response);
            assertFalse(response.results().isEmpty());
            assertEquals(nodeId, response.results().get(0).id());
            assertEquals("profile", response.results().get(0).resultType());
            assertEquals("https://linkedin.com/in/alice", response.results().get(0).linkedinUrl());
            assertTrue(response.results().get(0).score() > 0);
        }
    }

    @Test
    void search_withMatchContext_populatesGeographyBreakdown() throws Exception {
        when(consentService.hasActiveConsent(userId, "professional_matching")).thenReturn(true);
        when(embeddingService.generateEmbedding(anyString())).thenReturn(new float[]{0.5f, 0.5f});

        UUID nodeId = UUID.randomUUID();
        Object[] userRow = new Object[]{
                nodeId, "USER", "Alice Engineer", "Backend Developer",
                new String[]{"Java"}, "IT",
                Timestamp.from(Instant.now()),
                "{\"work_mode\":\"REMOTE\"}",
                0.85
        };
        when(objectMapper.readValue(
                eq((String) userRow[7]),
                org.mockito.ArgumentMatchers.<com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>>any()))
                .thenReturn(new LinkedHashMap<>(Map.of("work_mode", "REMOTE")));
        when(searchRepository.unifiedVectorSearch(any(float[].class), eq(userId),
                any(), any(), anyInt())).thenReturn(rowList(userRow));

        SearchService.MatchContext context = new SearchService.MatchContext("IT", org.peoplemesh.domain.enums.WorkMode.REMOTE, null);
        SearchResponse response = searchService.search(
                userId, "java developer", null, null, null, 10, 0, context);

        assertFalse(response.results().isEmpty());
        assertNotNull(response.results().get(0).breakdown());
        assertEquals("remote_friendly", response.results().get(0).breakdown().geographyReason());
        assertTrue(response.results().get(0).breakdown().geographyScore() > 0);
    }

    @Test
    void search_withNodeResult_returnsNodeType() {
        when(consentService.hasActiveConsent(userId, "professional_matching")).thenReturn(true);
        when(embeddingService.generateEmbedding(anyString())).thenReturn(new float[]{0.5f});

        UUID nodeId = UUID.randomUUID();
        Object[] nodeRow = new Object[]{
                nodeId, "PROJECT", "Cool Project", "A project about Java",
                new String[]{"java", "quarkus"}, "DE",
                Timestamp.from(Instant.now()),
                null,
                0.70
        };
        when(searchRepository.unifiedVectorSearch(any(float[].class), eq(userId),
                any(), any(), anyInt())).thenReturn(rowList(nodeRow));

        SearchResponse response = searchService.search(userId, "java project");

        assertNotNull(response);
        assertFalse(response.results().isEmpty());
        assertEquals("node", response.results().get(0).resultType());
    }

    @Test
    void search_withMustHaveSkills_matchesAndScores() throws Exception {
        SearchQueryParser parser = mock(SearchQueryParser.class);
        Field parsersField = SearchService.class.getDeclaredField("queryParsers");
        parsersField.setAccessible(true);
        parsersField.set(searchService, List.of(parser));

        SearchQuery parsed = new SearchQuery(
                new SearchQuery.MustHaveFilters(List.of("Java", "Python"),
                        List.of("Developer"), List.of(), List.of(), List.of()),
                null, "senior", null, List.of("Java", "Python"), "java python developer", null);
        when(parser.parse("java python developer")).thenReturn(Optional.of(parsed));

        when(consentService.hasActiveConsent(userId, "professional_matching")).thenReturn(true);
        when(embeddingService.generateEmbedding("java python developer")).thenReturn(new float[]{0.5f});

        UUID nodeId = UUID.randomUUID();
        Object[] row = new Object[]{
                nodeId, "USER", "Bob", "Python Dev",
                new String[]{"Python", "Django"}, "US",
                Timestamp.from(Instant.now()),
                "{\"seniority\":\"SENIOR\",\"languages_spoken\":[\"English\"]}",
                0.80
        };
        when(searchRepository.unifiedVectorSearch(any(float[].class), eq(userId),
                any(), any(), anyInt())).thenReturn(rowList(row));

        try (MockedStatic<SkillAssessment> sa = mockStatic(SkillAssessment.class)) {
            sa.when(() -> SkillAssessment.findByNode(any())).thenReturn(Collections.emptyList());
            SearchResponse response = searchService.search(userId, "java python developer");
            assertNotNull(response);
            assertFalse(response.results().isEmpty());
        }
    }

    @Test
    void search_withSeniorityRerank_adjustsScores() throws Exception {
        SearchQueryParser parser = mock(SearchQueryParser.class);
        Field parsersField = SearchService.class.getDeclaredField("queryParsers");
        parsersField.setAccessible(true);
        parsersField.set(searchService, List.of(parser));

        SearchQuery parsed = new SearchQuery(
                null, null, "SENIOR", null, List.of("devops"), "devops", null);
        when(parser.parse("senior devops")).thenReturn(Optional.of(parsed));

        when(consentService.hasActiveConsent(userId, "professional_matching")).thenReturn(true);
        when(embeddingService.generateEmbedding("devops")).thenReturn(new float[]{0.5f});

        UUID nodeId = UUID.randomUUID();
        Object[] row = new Object[]{
                nodeId, "USER", "Charlie", "DevOps",
                new String[]{"Kubernetes"}, "US",
                Timestamp.from(Instant.now()),
                "{\"seniority\":\"SENIOR\"}",
                0.75
        };
        when(searchRepository.unifiedVectorSearch(any(float[].class), eq(userId),
                any(), any(), anyInt())).thenReturn(rowList(row));

        try (MockedStatic<SkillAssessment> sa = mockStatic(SkillAssessment.class)) {
            sa.when(() -> SkillAssessment.findByNode(any())).thenReturn(Collections.emptyList());
            SearchResponse response = searchService.search(userId, "senior devops");
            assertNotNull(response);
        }
    }

    @Test
    void search_malformedStructuredData_handlesGracefully() {
        when(consentService.hasActiveConsent(userId, "professional_matching")).thenReturn(true);
        when(embeddingService.generateEmbedding(anyString())).thenReturn(new float[]{0.5f});

        UUID nodeId = UUID.randomUUID();
        Object[] row = new Object[]{
                nodeId, "USER", "Broken", "desc",
                new String[]{}, "US",
                Timestamp.from(Instant.now()),
                "not-valid-json{{{",
                0.60
        };
        when(searchRepository.unifiedVectorSearch(any(float[].class), eq(userId),
                any(), any(), anyInt())).thenReturn(rowList(row));

        try (MockedStatic<SkillAssessment> sa = mockStatic(SkillAssessment.class)) {
            sa.when(() -> SkillAssessment.findByNode(any())).thenReturn(Collections.emptyList());
            SearchResponse response = searchService.search(userId, "test");
            assertNotNull(response);
        }
    }

    @Test
    void search_niceToHaveSkills_boost() throws Exception {
        SearchQueryParser parser = mock(SearchQueryParser.class);
        Field parsersField = SearchService.class.getDeclaredField("queryParsers");
        parsersField.setAccessible(true);
        parsersField.set(searchService, List.of(parser));

        SearchQuery parsed = new SearchQuery(
                new SearchQuery.MustHaveFilters(List.of("Java"),
                        List.of(), List.of(), List.of(), List.of()),
                new SearchQuery.NiceToHaveFilters(List.of("Docker", "Kubernetes"),
                        List.of(), List.of()),
                "unknown", null, List.of("Java"), "java docker kubernetes", null);
        when(parser.parse("java with docker")).thenReturn(Optional.of(parsed));

        when(consentService.hasActiveConsent(userId, "professional_matching")).thenReturn(true);
        when(embeddingService.generateEmbedding(anyString())).thenReturn(new float[]{0.5f});

        UUID id = UUID.randomUUID();
        Object[] row = new Object[]{
                id, "USER", "Expert", "Full stack",
                new String[]{"Java", "Docker"}, "US",
                Timestamp.from(Instant.now()),
                null,
                0.80
        };
        when(searchRepository.unifiedVectorSearch(any(float[].class), eq(userId),
                any(), any(), anyInt())).thenReturn(rowList(row));

        try (MockedStatic<SkillAssessment> sa = mockStatic(SkillAssessment.class)) {
            sa.when(() -> SkillAssessment.findByNode(any())).thenReturn(Collections.emptyList());
            SearchResponse response = searchService.search(userId, "java with docker");
            assertFalse(response.results().isEmpty());
        }
    }

    @Test
    void search_industriesMatching_addsScore() throws Exception {
        SearchQueryParser parser = mock(SearchQueryParser.class);
        Field parsersField = SearchService.class.getDeclaredField("queryParsers");
        parsersField.setAccessible(true);
        parsersField.set(searchService, List.of(parser));

        SearchQuery parsed = new SearchQuery(
                new SearchQuery.MustHaveFilters(List.of("Python"),
                        List.of(), List.of(), List.of(), List.of("Finance")),
                null, "unknown", null, List.of("Python"), "python finance", null);
        when(parser.parse("python in finance")).thenReturn(Optional.of(parsed));

        when(consentService.hasActiveConsent(userId, "professional_matching")).thenReturn(true);
        when(embeddingService.generateEmbedding(anyString())).thenReturn(new float[]{0.5f});

        UUID id = UUID.randomUUID();
        Object[] row = new Object[]{
                id, "USER", "Analyst", "Data analyst",
                new String[]{"Python"}, "UK",
                Timestamp.from(Instant.now()),
                "{\"industries\":[\"Finance\",\"Banking\"]}",
                0.70
        };
        when(searchRepository.unifiedVectorSearch(any(float[].class), eq(userId),
                any(), any(), anyInt())).thenReturn(rowList(row));

        try (MockedStatic<SkillAssessment> sa = mockStatic(SkillAssessment.class)) {
            sa.when(() -> SkillAssessment.findByNode(any())).thenReturn(Collections.emptyList());
            SearchResponse response = searchService.search(userId, "python in finance");
            assertFalse(response.results().isEmpty());
        }
    }

    @Test
    void search_multipleResults_sortsAndLimits() {
        when(consentService.hasActiveConsent(userId, "professional_matching")).thenReturn(true);
        when(embeddingService.generateEmbedding(anyString())).thenReturn(new float[]{0.5f});

        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            rows.add(new Object[]{
                    UUID.randomUUID(), "USER", "User" + i, "desc",
                    new String[]{}, "US",
                    Timestamp.from(Instant.now()),
                    null,
                    0.90 - i * 0.1
            });
        }
        when(searchRepository.unifiedVectorSearch(any(float[].class), eq(userId),
                any(), any(), anyInt())).thenReturn(rows);

        try (MockedStatic<SkillAssessment> sa = mockStatic(SkillAssessment.class)) {
            sa.when(() -> SkillAssessment.findByNode(any())).thenReturn(Collections.emptyList());
            SearchResponse response = searchService.search(userId, "test");
            assertNotNull(response);
            assertTrue(response.results().size() <= 20);
            for (int i = 1; i < response.results().size(); i++) {
                assertTrue(response.results().get(i - 1).score() >= response.results().get(i).score());
            }
        }
    }

    @Test
    void search_withLimitAndOffset_returnsPagedSlice() {
        when(consentService.hasActiveConsent(userId, "professional_matching")).thenReturn(true);
        when(embeddingService.generateEmbedding(anyString())).thenReturn(new float[]{0.5f});

        UUID id0 = UUID.randomUUID();
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        List<Object[]> rows = List.of(
                new Object[]{id0, "USER", "User0", "desc", new String[]{}, "US", Timestamp.from(Instant.now()), null, 0.95},
                new Object[]{id1, "USER", "User1", "desc", new String[]{}, "US", Timestamp.from(Instant.now()), null, 0.90},
                new Object[]{id2, "USER", "User2", "desc", new String[]{}, "US", Timestamp.from(Instant.now()), null, 0.85}
        );
        when(searchRepository.unifiedVectorSearch(any(float[].class), eq(userId),
                any(), any(), anyInt())).thenReturn(rows);

        try (MockedStatic<SkillAssessment> sa = mockStatic(SkillAssessment.class)) {
            sa.when(() -> SkillAssessment.findByNode(any())).thenReturn(Collections.emptyList());
            SearchResponse response = searchService.search(userId, "test", 1, 1);
            assertEquals(1, response.results().size());
            assertEquals(id1, response.results().get(0).id());
        }
    }

    @Test
    void search_languageFilter_passedToRepository() throws Exception {
        SearchQueryParser parser = mock(SearchQueryParser.class);
        Field parsersField = SearchService.class.getDeclaredField("queryParsers");
        parsersField.setAccessible(true);
        parsersField.set(searchService, List.of(parser));

        SearchQuery parsed = new SearchQuery(
                new SearchQuery.MustHaveFilters(List.of(), List.of(),
                        List.of("Italian"), List.of(), List.of()),
                null, "unknown", null, List.of(), "Italian speaker", null);
        when(parser.parse("Italian speaker")).thenReturn(Optional.of(parsed));

        when(consentService.hasActiveConsent(userId, "professional_matching")).thenReturn(true);
        when(embeddingService.generateEmbedding(anyString())).thenReturn(new float[]{0.5f});
        when(searchRepository.unifiedVectorSearch(any(float[].class), eq(userId),
                any(), eq(List.of("Italian")), anyInt())).thenReturn(Collections.emptyList());

        searchService.search(userId, "Italian speaker");

        verify(searchRepository).unifiedVectorSearch(any(float[].class), eq(userId),
                any(), eq(List.of("Italian")), anyInt());
    }

    private static List<Object[]> rowList(Object[]... rows) {
        return new ArrayList<>(Arrays.asList(rows));
    }
}
