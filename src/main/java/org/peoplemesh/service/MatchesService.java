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
import org.peoplemesh.util.MatchingUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class MatchesService {

    @Inject
    MatchingService matchingService;

    @Inject
    SearchService searchService;

    @Inject
    ProfileSearchQueryBuilder profileSearchQueryBuilder;

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
        SearchService.MatchContext context = resolveMatchContext(userId);
        SearchResponse searchResponse = searchService.search(
                userId, resolveSearchQueryText(parsedQuery), parsedQuery, type, country, limit, offset, context);
        return toMeshResults(searchResponse.results());
    }

    public SearchResponse matchFromPrompt(UUID userId, SearchRequest request) {
        return matchFromPrompt(userId, request, null, null);
    }

    public SearchResponse matchFromPrompt(UUID userId, SearchRequest request, Integer limit, Integer offset) {
        SearchService.MatchContext context = resolveMatchContext(userId);
        return searchService.search(userId, request.query(), null, null, null, limit, offset, context);
    }

    public List<MeshMatchResult> matchMyProfile(UUID userId, String type, String country) {
        return matchMyProfile(userId, type, country, null, null);
    }

    public List<MeshMatchResult> matchMyProfile(UUID userId, String type, String country, Integer limit, Integer offset) {
        Optional<MeshNode> myNodeOpt = nodeRepository.findPublishedUserNode(userId);
        MeshNode myNode = myNodeOpt.isPresent() ? myNodeOpt.get() : null;
        if (myNode == null || myNode.embedding == null) {
            return List.of();
        }
        SearchQuery profileQuery = profileSearchQueryBuilder.buildFromUserNode(myNode);
        SearchService.MatchContext context = new SearchService.MatchContext(
                myNode.country,
                MatchingUtils.structuredWorkMode(myNode),
                MatchingUtils.structuredEmploymentType(myNode)
        );
        SearchResponse searchResponse = searchService.search(
                userId,
                resolveSearchQueryText(profileQuery),
                profileQuery,
                type,
                country,
                limit,
                offset,
                context
        );
        return toMeshResults(searchResponse.results());
    }

    public List<MeshMatchResult> matchFromNode(UUID userId, UUID nodeId, String type, String country) {
        Optional<MeshNode> nodeOpt = nodeRepository.findById(nodeId);
        MeshNode node = nodeOpt.isPresent() ? nodeOpt.get() : null;
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

    private SearchService.MatchContext resolveMatchContext(UUID userId) {
        Optional<MeshNode> myNodeOpt = nodeRepository.findPublishedUserNode(userId);
        MeshNode myNode = myNodeOpt.isPresent() ? myNodeOpt.get() : null;
        if (myNode == null) {
            return SearchService.MatchContext.empty();
        }
        return new SearchService.MatchContext(
                myNode.country,
                MatchingUtils.structuredWorkMode(myNode),
                MatchingUtils.structuredEmploymentType(myNode)
        );
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
        List<String> commonItems = new java.util.ArrayList<>();
        if (breakdown.matchedMustHaveSkills() != null) {
            commonItems.addAll(breakdown.matchedMustHaveSkills());
        }
        if (breakdown.matchedNiceToHaveSkills() != null) {
            for (String skill : breakdown.matchedNiceToHaveSkills()) {
                if (!commonItems.contains(skill)) {
                    commonItems.add(skill);
                }
            }
        }
        return new MeshMatchResult.MeshMatchBreakdown(
                breakdown.embeddingScore(),
                breakdown.mustHaveSkillCoverage(),
                breakdown.geographyScore(),
                breakdown.finalScore(),
                1.0,
                breakdown.finalScore(),
                commonItems,
                breakdown.geographyReason()
        );
    }
}
