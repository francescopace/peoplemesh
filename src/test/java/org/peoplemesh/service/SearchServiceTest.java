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
import org.peoplemesh.domain.dto.ParsedSearchQuery;
import org.peoplemesh.domain.dto.SearchResponse;
import org.peoplemesh.domain.exception.RateLimitException;
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

    @InjectMocks
    SearchService searchService;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(config.search()).thenReturn(searchConfig);
        lenient().when(searchConfig.maxPerMinute()).thenReturn(100);
        lenient().when(searchConfig.minScore()).thenReturn(0.0);
        lenient().when(skillAssessmentRepository.findByNodeIds(anyList())).thenReturn(Collections.emptyMap());
        lenient().when(skillDefinitionRepository.findByIds(anyList())).thenReturn(Collections.emptyList());

        Field parsersField = SearchService.class.getDeclaredField("queryParsers");
        parsersField.setAccessible(true);
        parsersField.set(searchService, Collections.emptyList());
    }

    @Test
    void search_noConsent_returnsEmptyResponse() {
        when(consentService.hasActiveConsent(userId, "professional_matching")).thenReturn(false);

        SearchResponse response = searchService.search(userId, "java developer", null);

        assertNotNull(response);
        assertTrue(response.results().isEmpty());
        verifyNoInteractions(embeddingService);
    }

    @Test
    void search_rateLimited_throwsRateLimitException() {
        when(consentService.hasActiveConsent(userId, "professional_matching")).thenReturn(true);
        when(searchConfig.maxPerMinute()).thenReturn(0);

        assertThrows(RateLimitException.class,
                () -> searchService.search(userId, "query", null));
    }

    @Test
    void search_nullEmbedding_returnsEmptyResponse() {
        when(consentService.hasActiveConsent(userId, "professional_matching")).thenReturn(true);
        when(embeddingService.generateEmbedding(anyString())).thenReturn(null);

        SearchResponse response = searchService.search(userId, "some query", null);

        assertNotNull(response.parsedQuery());
        assertTrue(response.results().isEmpty());
    }

    @Test
    void search_withEmbedding_executesQuery() {
        when(consentService.hasActiveConsent(userId, "professional_matching")).thenReturn(true);
        when(embeddingService.generateEmbedding(anyString())).thenReturn(new float[]{0.1f, 0.2f});

        when(searchRepository.unifiedVectorSearch(any(float[].class), eq(userId),
                any(), any(), anyInt())).thenReturn(Collections.emptyList());

        SearchResponse response = searchService.search(userId, "java", null);

        assertNotNull(response);
        assertTrue(response.results().isEmpty());
        verify(searchRepository).unifiedVectorSearch(any(float[].class), eq(userId),
                any(), any(), anyInt());
    }

    @Test
    void search_withCountryFilter_addsSqlParameter() {
        when(consentService.hasActiveConsent(userId, "professional_matching")).thenReturn(true);
        when(embeddingService.generateEmbedding(anyString())).thenReturn(new float[]{0.1f});

        when(searchRepository.unifiedVectorSearch(any(float[].class), eq(userId),
                eq("IT"), any(), anyInt())).thenReturn(Collections.emptyList());

        searchService.search(userId, "query", "IT");

        verify(searchRepository).unifiedVectorSearch(any(float[].class), eq(userId),
                eq("IT"), any(), anyInt());
    }

    @Test
    void search_withParser_usesParserResult() throws Exception {
        SearchQueryParser parser = mock(SearchQueryParser.class);
        Field parsersField = SearchService.class.getDeclaredField("queryParsers");
        parsersField.setAccessible(true);
        parsersField.set(searchService, List.of(parser));

        ParsedSearchQuery parsed = new ParsedSearchQuery(null, null, "unknown", null,
                List.of("java"), "java developer");
        when(parser.parse("java developer")).thenReturn(Optional.of(parsed));

        when(consentService.hasActiveConsent(userId, "professional_matching")).thenReturn(true);
        when(embeddingService.generateEmbedding("java developer")).thenReturn(new float[]{0.5f});

        when(searchRepository.unifiedVectorSearch(any(float[].class), eq(userId),
                any(), any(), anyInt())).thenReturn(Collections.emptyList());

        SearchResponse response = searchService.search(userId, "java developer", null);

        verify(parser).parse("java developer");
        assertNotNull(response);
    }

    @Test
    void search_fallbackParse_filtersStopWords() {
        when(consentService.hasActiveConsent(userId, "professional_matching")).thenReturn(true);
        when(embeddingService.generateEmbedding(anyString())).thenReturn(null);

        SearchResponse response = searchService.search(userId, "looking for a java developer", null);

        assertNotNull(response.parsedQuery());
        assertFalse(response.parsedQuery().keywords().contains("looking"));
        assertFalse(response.parsedQuery().keywords().contains("for"));
        assertFalse(response.parsedQuery().keywords().contains("a"));
        assertTrue(response.parsedQuery().keywords().contains("java"));
    }

    @Test
    void rateLimitException_isRuntimeException() {
        var ex = new RateLimitException("test");
        assertInstanceOf(RuntimeException.class, ex);
        assertEquals("test", ex.getMessage());
    }

    @Test
    void search_withResults_scoresAndReturnsItems() {
        when(consentService.hasActiveConsent(userId, "professional_matching")).thenReturn(true);
        when(embeddingService.generateEmbedding(anyString())).thenReturn(new float[]{0.5f, 0.5f});

        UUID nodeId = UUID.randomUUID();
        Object[] userRow = new Object[]{
                nodeId, "USER", "Alice Engineer", "Backend Developer",
                new String[]{"Java", "Python"}, "US",
                Timestamp.from(Instant.now()),
                "{\"languages_spoken\":[\"English\"],\"city\":\"NYC\",\"email\":\"alice@test.com\"}",
                0.85
        };
        when(searchRepository.unifiedVectorSearch(any(float[].class), eq(userId),
                any(), any(), anyInt())).thenReturn(rowList(userRow));

        try (MockedStatic<SkillAssessment> sa = mockStatic(SkillAssessment.class)) {
            sa.when(() -> SkillAssessment.findByNode(any())).thenReturn(Collections.emptyList());

            SearchResponse response = searchService.search(userId, "java developer", null);

            assertNotNull(response);
            assertFalse(response.results().isEmpty());
            assertEquals(nodeId, response.results().get(0).id());
            assertEquals("profile", response.results().get(0).resultType());
            assertTrue(response.results().get(0).score() > 0);
        }
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

        SearchResponse response = searchService.search(userId, "java project", null);

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

        ParsedSearchQuery parsed = new ParsedSearchQuery(
                new ParsedSearchQuery.MustHaveFilters(List.of("Java", "Python"),
                        List.of("Developer"), List.of(), List.of(), List.of()),
                null, "senior", null, List.of("Java", "Python"), "java python developer");
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
            SearchResponse response = searchService.search(userId, "java python developer", null);
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

        ParsedSearchQuery parsed = new ParsedSearchQuery(
                null, null, "SENIOR", null, List.of("devops"), "devops");
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
            SearchResponse response = searchService.search(userId, "senior devops", null);
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
            SearchResponse response = searchService.search(userId, "test", null);
            assertNotNull(response);
        }
    }

    @Test
    void search_niceToHaveSkills_boost() throws Exception {
        SearchQueryParser parser = mock(SearchQueryParser.class);
        Field parsersField = SearchService.class.getDeclaredField("queryParsers");
        parsersField.setAccessible(true);
        parsersField.set(searchService, List.of(parser));

        ParsedSearchQuery parsed = new ParsedSearchQuery(
                new ParsedSearchQuery.MustHaveFilters(List.of("Java"),
                        List.of(), List.of(), List.of(), List.of()),
                new ParsedSearchQuery.NiceToHaveFilters(List.of("Docker", "Kubernetes"),
                        List.of(), List.of()),
                "unknown", null, List.of("Java"), "java docker kubernetes");
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
            SearchResponse response = searchService.search(userId, "java with docker", null);
            assertFalse(response.results().isEmpty());
        }
    }

    @Test
    void search_industriesMatching_addsScore() throws Exception {
        SearchQueryParser parser = mock(SearchQueryParser.class);
        Field parsersField = SearchService.class.getDeclaredField("queryParsers");
        parsersField.setAccessible(true);
        parsersField.set(searchService, List.of(parser));

        ParsedSearchQuery parsed = new ParsedSearchQuery(
                new ParsedSearchQuery.MustHaveFilters(List.of("Python"),
                        List.of(), List.of(), List.of(), List.of("Finance")),
                null, "unknown", null, List.of("Python"), "python finance");
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
            SearchResponse response = searchService.search(userId, "python in finance", null);
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
            SearchResponse response = searchService.search(userId, "test", null);
            assertNotNull(response);
            assertTrue(response.results().size() <= 20);
            for (int i = 1; i < response.results().size(); i++) {
                assertTrue(response.results().get(i - 1).score() >= response.results().get(i).score());
            }
        }
    }

    @Test
    void search_languageFilter_passedToRepository() throws Exception {
        SearchQueryParser parser = mock(SearchQueryParser.class);
        Field parsersField = SearchService.class.getDeclaredField("queryParsers");
        parsersField.setAccessible(true);
        parsersField.set(searchService, List.of(parser));

        ParsedSearchQuery parsed = new ParsedSearchQuery(
                new ParsedSearchQuery.MustHaveFilters(List.of(), List.of(),
                        List.of("Italian"), List.of(), List.of()),
                null, "unknown", null, List.of(), "Italian speaker");
        when(parser.parse("Italian speaker")).thenReturn(Optional.of(parsed));

        when(consentService.hasActiveConsent(userId, "professional_matching")).thenReturn(true);
        when(embeddingService.generateEmbedding(anyString())).thenReturn(new float[]{0.5f});
        when(searchRepository.unifiedVectorSearch(any(float[].class), eq(userId),
                any(), eq(List.of("Italian")), anyInt())).thenReturn(Collections.emptyList());

        searchService.search(userId, "Italian speaker", null);

        verify(searchRepository).unifiedVectorSearch(any(float[].class), eq(userId),
                any(), eq(List.of("Italian")), anyInt());
    }

    private static List<Object[]> rowList(Object[]... rows) {
        return new ArrayList<>(Arrays.asList(rows));
    }
}
