package org.peoplemesh.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.domain.dto.MeshMatchResult;
import org.peoplemesh.domain.dto.SearchMatchBreakdown;
import org.peoplemesh.domain.dto.SearchQuery;
import org.peoplemesh.domain.dto.SearchRequest;
import org.peoplemesh.domain.dto.SearchResultItem;
import org.peoplemesh.domain.dto.SearchResponse;
import org.peoplemesh.domain.dto.SearchOptions;
import org.peoplemesh.domain.enums.EmploymentType;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.enums.Seniority;
import org.peoplemesh.domain.enums.WorkMode;
import org.peoplemesh.domain.exception.ForbiddenBusinessException;
import org.peoplemesh.domain.exception.NotFoundBusinessException;
import org.peoplemesh.domain.model.MeshNode;
import org.peoplemesh.repository.NodeRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchesServiceTest {

    @Mock
    MatchingService matchingService;
    @Mock
    SearchService searchService;
    @Mock
    ProfileSearchQueryBuilder profileSearchQueryBuilder;
    @Mock
    NodeRepository nodeRepository;
    @Mock
    NodeAccessPolicyService nodeAccessPolicyService;

    @InjectMocks
    MatchesService service;

    @Test
    void matchFromSchemaParsedQuery_delegatesToSearchService() {
        UUID userId = UUID.randomUUID();
        SearchQuery parsedQuery = new SearchQuery(
                null, null, "unknown", null, List.of("java"), "java developer", "people");
        SearchResponse expected = new SearchResponse(parsedQuery, List.of());
        when(nodeRepository.findPublishedUserNode(userId)).thenReturn(Optional.empty());
        when(searchService.search(userId, "java developer", parsedQuery, "PEOPLE", "IT", 5, 1,
                SearchService.MatchContext.empty()))
                .thenReturn(expected);

        List<MeshMatchResult> result = service.matchFromSchema(userId, parsedQuery, "PEOPLE", "IT", 5, 1);

        assertEquals(List.of(), result);
        verify(searchService).search(userId, "java developer", parsedQuery, "PEOPLE", "IT", 5, 1,
                SearchService.MatchContext.empty());
    }

    @Test
    void matchFromPrompt_delegatesToSearchService() {
        UUID userId = UUID.randomUUID();
        SearchRequest request = new SearchRequest("java");
        SearchResponse expected = new SearchResponse(null, List.of());
        when(nodeRepository.findPublishedUserNode(userId)).thenReturn(Optional.empty());
        when(searchService.search(userId, "java", null, null, null, 10, null,
                SearchService.MatchContext.empty())).thenReturn(expected);

        SearchResponse result = service.matchFromPrompt(userId, request, 10);

        assertEquals(expected, result);
        verify(searchService).search(userId, "java", null, null, null, 10, null,
                SearchService.MatchContext.empty());
    }

    @Test
    void matchFromPrompt_withFilters_delegatesToSearchService() {
        UUID userId = UUID.randomUUID();
        SearchRequest request = new SearchRequest("java developer");
        SearchResponse expected = new SearchResponse(null, List.of());
        when(nodeRepository.findPublishedUserNode(userId)).thenReturn(Optional.empty());
        when(searchService.search(userId, "java developer", null, "PEOPLE", "IT", 10, null,
                SearchService.MatchContext.empty())).thenReturn(expected);

        SearchResponse result = service.matchFromPrompt(userId, request, "PEOPLE", "IT", 10);

        assertEquals(expected, result);
        verify(searchService).search(userId, "java developer", null, "PEOPLE", "IT", 10, null,
                SearchService.MatchContext.empty());
    }

    @Test
    void matchMyProfile_delegatesToSearchServiceWithProfileQuery() {
        UUID userId = UUID.randomUUID();
        MeshNode myNode = new MeshNode();
        myNode.id = userId;
        myNode.embedding = new float[]{0.1f};
        myNode.country = "IT";
        SearchQuery query = new SearchQuery(
                null, null, "unknown", null, List.of("java"), "java", "all");
        SearchResponse expected = new SearchResponse(query, List.of());
        when(nodeRepository.findPublishedUserNode(userId)).thenReturn(Optional.of(myNode));
        when(profileSearchQueryBuilder.buildFromUserNode(myNode, null)).thenReturn(query);
        when(searchService.search(
                eq(userId),
                eq("java"),
                eq(query),
                eq("PEOPLE"),
                eq("IT"),
                eq(10),
                eq(20),
                any(SearchService.MatchContext.class),
                eq(null)))
                .thenReturn(expected);

        List<MeshMatchResult> result = service.matchMyProfile(userId, "PEOPLE", "IT", 10, 20);

        assertEquals(List.of(), result);
        verify(profileSearchQueryBuilder).buildFromUserNode(myNode, null);
        verify(searchService).search(
                eq(userId),
                eq("java"),
                eq(query),
                eq("PEOPLE"),
                eq("IT"),
                eq(10),
                eq(20),
                any(SearchService.MatchContext.class),
                eq(null));
    }

    @Test
    void matchFromNode_whenMissingOrNoEmbedding_throwsNotFound() {
        UUID userId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.empty());

        assertThrows(NotFoundBusinessException.class,
                () -> service.matchFromNode(userId, nodeId, "PEOPLE", "IT"));
    }

    @Test
    void matchFromNode_whenAccessDenied_throwsForbidden() {
        UUID userId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        MeshNode node = new MeshNode();
        node.id = nodeId;
        node.embedding = new float[]{0.2f};
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(node));
        when(nodeAccessPolicyService.canReadNode(userId, node)).thenReturn(false);

        assertThrows(ForbiddenBusinessException.class,
                () -> service.matchFromNode(userId, nodeId, "PEOPLE", "IT"));
    }

    @Test
    void matchFromNode_whenAllowed_delegatesToMatchingService() {
        UUID userId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        MeshNode node = new MeshNode();
        node.id = nodeId;
        node.embedding = new float[]{0.2f};
        List<MeshMatchResult> expected = List.of();

        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(node));
        when(nodeAccessPolicyService.canReadNode(userId, node)).thenReturn(true);
        when(matchingService.findAllMatches(userId, node.embedding, "PEOPLE", "IT")).thenReturn(expected);

        List<MeshMatchResult> result = service.matchFromNode(userId, nodeId, "PEOPLE", "IT");

        assertEquals(expected, result);
        verify(matchingService).findAllMatches(userId, node.embedding, "PEOPLE", "IT");
    }

    @Test
    void matchFromSchema_withKeywordOnlyQuery_usesJoinedKeywords() {
        UUID userId = UUID.randomUUID();
        SearchQuery parsedQuery = new SearchQuery(
                null, null, "unknown", null, List.of("java", "spring"), null, "people");
        when(nodeRepository.findPublishedUserNode(userId)).thenReturn(Optional.empty());
        when(searchService.search(userId, "java spring", parsedQuery, "PEOPLE", null, 5, 0,
                SearchService.MatchContext.empty()))
                .thenReturn(new SearchResponse(parsedQuery, List.of()));

        List<MeshMatchResult> out = service.matchFromSchema(userId, parsedQuery, "PEOPLE", null, 5, 0);

        assertTrue(out.isEmpty());
        verify(searchService).search(userId, "java spring", parsedQuery, "PEOPLE", null, 5, 0,
                SearchService.MatchContext.empty());
    }

    @Test
    void matchFromSchema_withEmptyTextAndKeywords_usesFallbackSearchText() {
        UUID userId = UUID.randomUUID();
        SearchQuery parsedQuery = new SearchQuery(
                null, null, "unknown", null, List.of(), "   ", "people");
        when(nodeRepository.findPublishedUserNode(userId)).thenReturn(Optional.empty());
        when(searchService.search(userId, "search", parsedQuery, null, null, null, null,
                SearchService.MatchContext.empty()))
                .thenReturn(new SearchResponse(parsedQuery, List.of()));

        List<MeshMatchResult> out = service.matchFromSchema(userId, parsedQuery, null, null, null, null);

        assertTrue(out.isEmpty());
        verify(searchService).search(userId, "search", parsedQuery, null, null, null, null,
                SearchService.MatchContext.empty());
    }

    @Test
    void matchFromSchema_mapsProfileAndNodeItemsIntoMeshResults() {
        UUID userId = UUID.randomUUID();
        SearchQuery parsedQuery = new SearchQuery(
                null, null, "unknown", null, List.of("java"), "java", "all");
        SearchMatchBreakdown profileBreakdown = new SearchMatchBreakdown(
                0.91, 0.5, 0.2, 0.0, 0.0, 1.0, 0.87,
                List.of("Java", "SQL"),
                List.of("SQL", "Kubernetes"),
                List.of(),
                List.of("SEMANTIC_SIMILARITY"),
                "same_country",
                true,
                true,
                false,
                false,
                true,
                1.0,
                1.0,
                0.0,
                0.0,
                0.7,
                0.2,
                0.3,
                0.1,
                0.1,
                0.2,
                0.05,
                0.0
        );
        SearchResultItem profile = SearchResultItem.profile(
                UUID.randomUUID(),
                0.87,
                "Alice",
                "https://avatar",
                List.of("Engineer"),
                Seniority.MID,
                List.of("Java", "SQL"),
                List.of("Docker"),
                List.of("EN"),
                "IT",
                "Milan",
                WorkMode.REMOTE,
                EmploymentType.EMPLOYED,
                "@alice",
                "alice@example.com",
                "@alice_tg",
                "555",
                "https://linkedin",
                profileBreakdown,
                Map.of("Java", 4)
        );
        SearchResultItem node = SearchResultItem.node(
                UUID.randomUUID(),
                0.55,
                NodeType.JOB,
                "Backend Engineer",
                "Role description",
                List.of("java", "backend"),
                "IT",
                null
        );
        SearchResponse response = new SearchResponse(parsedQuery, List.of(profile, node));

        when(nodeRepository.findPublishedUserNode(userId)).thenReturn(Optional.empty());
        when(searchService.search(userId, "java", parsedQuery, null, null, null, null,
                SearchService.MatchContext.empty())).thenReturn(response);

        List<MeshMatchResult> out = service.matchFromSchema(userId, parsedQuery, null, null, null, null);

        assertEquals(2, out.size());
        MeshMatchResult people = out.get(0);
        assertEquals("PEOPLE", people.nodeType());
        assertEquals("Alice", people.title());
        assertEquals(List.of("Java", "SQL", "Kubernetes"), people.breakdown().commonItems());
        assertEquals(List.of("Java", "SQL"), people.breakdown().matchedMustHaveSkills());
        assertEquals(List.of("SQL", "Kubernetes"), people.breakdown().matchedNiceToHaveSkills());
        assertEquals(List.of("SEMANTIC_SIMILARITY"), people.breakdown().reasonCodes());
        assertEquals("same_country", people.breakdown().geographyReason());
        assertEquals(0.7, people.breakdown().weightEmbedding());
        assertEquals(1.0, people.breakdown().mustHavePenaltyFactor());
        assertEquals("MID", people.person().seniority());
        assertEquals(4, people.person().skillLevels().get("Java"));

        MeshMatchResult job = out.get(1);
        assertEquals("JOB", job.nodeType());
        assertEquals("Backend Engineer", job.title());
        assertNull(job.person());
        assertNull(job.breakdown());
    }

    @Test
    void matchMyProfile_withTuning_allowsOverrides() {
        UUID userId = UUID.randomUUID();
        MeshNode myNode = new MeshNode();
        myNode.id = userId;
        myNode.embedding = new float[]{0.1f};
        SearchOptions tuning = new SearchOptions(
                0.7, null, null, null, null, null, null,
                null, null, null, null, null);
        SearchQuery query = new SearchQuery(null, null, "unknown", null, List.of("java"), "java", "all");
        when(nodeRepository.findPublishedUserNode(userId)).thenReturn(Optional.of(myNode));
        when(profileSearchQueryBuilder.buildFromUserNode(myNode, tuning)).thenReturn(query);
        when(searchService.search(
                eq(userId), eq("java"), eq(query), eq("PEOPLE"), eq(null), eq(10), eq(0),
                any(SearchService.MatchContext.class), eq(tuning)))
                .thenReturn(new SearchResponse(query, List.of()));

        List<MeshMatchResult> out = service.matchMyProfile(userId, "PEOPLE", null, 10, 0, tuning);

        assertTrue(out.isEmpty());
    }
}
