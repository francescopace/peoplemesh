package org.peoplemesh.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.peoplemesh.domain.dto.MeshMatchResult;
import org.peoplemesh.domain.dto.SearchQuery;
import org.peoplemesh.domain.dto.SearchMatchBreakdown;
import org.peoplemesh.domain.dto.SearchRequest;
import org.peoplemesh.domain.dto.SearchResultItem;
import org.peoplemesh.domain.dto.SearchResponse;
import org.peoplemesh.domain.exception.ForbiddenBusinessException;
import org.peoplemesh.domain.exception.NotFoundBusinessException;
import org.peoplemesh.domain.model.MeshNode;
import org.peoplemesh.repository.NodeRepository;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class MatchesService {

    @Inject
    MatchingService matchingService;

    @Inject
    SearchService searchService;

    @Inject
    NodeRepository nodeRepository;

    @Inject
    NodeAccessPolicyService nodeAccessPolicyService;

    public List<MeshMatchResult> matchFromSchema(
            UUID userId,
            SearchQuery parsedQuery,
            String type,
            String country,
            Integer limit,
            Integer offset) {
        SearchResponse searchResponse = searchService.search(
                userId, resolveSearchQueryText(parsedQuery), parsedQuery, type, country, limit, offset);
        return toMeshResults(searchResponse.results());
    }

    public SearchResponse matchFromPrompt(UUID userId, SearchRequest request) {
        return matchFromPrompt(userId, request, null, null);
    }

    public SearchResponse matchFromPrompt(UUID userId, SearchRequest request, Integer limit, Integer offset) {
        return searchService.search(userId, request.query(), limit, offset);
    }

    public List<MeshMatchResult> matchMyProfile(UUID userId, String type, String country) {
        return matchMyProfile(userId, type, country, null, null);
    }

    public List<MeshMatchResult> matchMyProfile(UUID userId, String type, String country, Integer limit, Integer offset) {
        return matchingService.findAllMatches(userId, type, country, limit, offset);
    }

    public List<MeshMatchResult> matchFromNode(UUID userId, UUID nodeId, String type, String country) {
        MeshNode node = nodeRepository.findById(nodeId).orElse(null);
        if (node == null || node.embedding == null) {
            throw new NotFoundBusinessException("Node not found or has no embedding");
        }
        if (!nodeAccessPolicyService.canReadNode(userId, node)) {
            throw new ForbiddenBusinessException("You do not have access to this node");
        }
        return matchingService.findAllMatches(userId, node.embedding, type, country);
    }

    private String resolveSearchQueryText(SearchQuery parsedQuery) {
        if (parsedQuery.embeddingText() != null && !parsedQuery.embeddingText().isBlank()) {
            return parsedQuery.embeddingText();
        }
        if (parsedQuery.keywords() != null && !parsedQuery.keywords().isEmpty()) {
            return String.join(" ", parsedQuery.keywords());
        }
        return "search";
    }

    private List<MeshMatchResult> toMeshResults(List<SearchResultItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream().map(this::toMeshResult).toList();
    }

    private MeshMatchResult toMeshResult(SearchResultItem item) {
        boolean isProfile = "profile".equalsIgnoreCase(item.resultType());
        MeshMatchResult.MeshMatchBreakdown breakdown = toMeshBreakdown(item.breakdown());
        if (isProfile) {
            MeshMatchResult.PersonDetails person = new MeshMatchResult.PersonDetails(
                    item.roles(),
                    item.seniority() != null ? item.seniority().name() : null,
                    item.skillsTechnical(),
                    item.toolsAndTech(),
                    List.of(),
                    item.workMode() != null ? item.workMode().name() : null,
                    item.employmentType() != null ? item.employmentType().name() : null,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    item.city(),
                    null,
                    item.slackHandle(),
                    item.email(),
                    item.telegramHandle(),
                    item.mobilePhone(),
                    item.linkedinUrl()
            );
            return new MeshMatchResult(
                    item.id(),
                    "PEOPLE",
                    item.displayName(),
                    null,
                    item.avatarUrl(),
                    item.skillsTechnical(),
                    item.country(),
                    item.score(),
                    breakdown,
                    person
            );
        }
        String nodeType = item.nodeType() != null ? item.nodeType().name() : "UNKNOWN";
        return new MeshMatchResult(
                item.id(),
                nodeType,
                item.title(),
                item.description(),
                null,
                item.tags(),
                item.country(),
                item.score(),
                breakdown,
                null
        );
    }

    private MeshMatchResult.MeshMatchBreakdown toMeshBreakdown(SearchMatchBreakdown breakdown) {
        if (breakdown == null) {
            return null;
        }
        return new MeshMatchResult.MeshMatchBreakdown(
                breakdown.embeddingScore(),
                breakdown.mustHaveSkillCoverage(),
                0.0,
                breakdown.finalScore(),
                1.0,
                breakdown.finalScore(),
                breakdown.matchedMustHaveSkills(),
                null
        );
    }
}
