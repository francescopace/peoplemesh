package org.peoplemesh.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.domain.dto.MeshMatchResult;
import org.peoplemesh.domain.dto.SearchQuery;
import org.peoplemesh.domain.dto.SearchRequest;
import org.peoplemesh.domain.dto.SearchResponse;
import org.peoplemesh.domain.exception.ForbiddenBusinessException;
import org.peoplemesh.domain.exception.NotFoundBusinessException;
import org.peoplemesh.domain.model.MeshNode;
import org.peoplemesh.repository.NodeRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchesServiceTest {

    @Mock
    MatchingService matchingService;
    @Mock
    SearchService searchService;
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
        when(searchService.search(userId, "java developer", parsedQuery, "PEOPLE", "IT", 5, 1))
                .thenReturn(expected);

        List<MeshMatchResult> result = service.matchFromSchema(userId, parsedQuery, "PEOPLE", "IT", 5, 1);

        assertEquals(List.of(), result);
        verify(searchService).search(userId, "java developer", parsedQuery, "PEOPLE", "IT", 5, 1);
    }

    @Test
    void matchFromPrompt_delegatesToSearchService() {
        UUID userId = UUID.randomUUID();
        SearchRequest request = new SearchRequest("java");
        SearchResponse expected = new SearchResponse(null, List.of());
        when(searchService.search(userId, "java", 10, 20)).thenReturn(expected);

        SearchResponse result = service.matchFromPrompt(userId, request, 10, 20);

        assertEquals(expected, result);
        verify(searchService).search(userId, "java", 10, 20);
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
}
