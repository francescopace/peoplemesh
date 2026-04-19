package org.peoplemesh.service;

import static org.peoplemesh.util.StructuredDataUtils.sdListOrEmpty;
import static org.peoplemesh.util.StructuredDataUtils.sdString;

import io.quarkus.arc.All;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.repository.MeshNodeSearchRepository;
import org.peoplemesh.util.GeographyUtils;
import org.peoplemesh.util.SearchMatchingUtils;
import org.peoplemesh.util.SqlParsingUtils;
import org.peoplemesh.util.StringUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.peoplemesh.domain.dto.*;
import org.peoplemesh.domain.enums.EmploymentType;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.enums.Seniority;
import org.peoplemesh.domain.enums.WorkMode;
import java.time.Instant;
import java.util.*;

@ApplicationScoped
public class SearchService {

    private static final Logger LOG = Logger.getLogger(SearchService.class);

    private static final int VECTOR_POOL_SIZE = 100;
    private static final int DEFAULT_RESULT_LIMIT = 20;

    @Inject @All
    List<SearchQueryParser> queryParsers;

    @Inject
    HeuristicSearchQueryParser heuristicSearchQueryParser;

    @Inject
    EmbeddingService embeddingService;

    @Inject
    MeshNodeSearchRepository searchRepository;

    @Inject
    AppConfig config;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    ConsentService consentService;

    @Inject
    SkillLevelResolutionService skillLevelResolutionService;

    @Inject
    SearchScoringEngine searchScoringEngine;

    public SearchResponse search(UUID userId, String queryText) {
        return search(userId, queryText, null, null, null, null, null, MatchContext.empty());
    }

    public SearchResponse search(UUID userId, String queryText, Integer limit, Integer offset) {
        return search(userId, queryText, null, null, null, limit, offset, MatchContext.empty());
    }

    public SearchResponse search(
            UUID userId,
            String queryText,
            SearchQuery preParsedQuery,
            String requestedType,
            String requestedCountry,
            Integer limit,
            Integer offset) {
        return search(
                userId,
                queryText,
                preParsedQuery,
                requestedType,
                requestedCountry,
                limit,
                offset,
                MatchContext.empty());
    }

    public SearchResponse search(
            UUID userId,
            String queryText,
            SearchQuery preParsedQuery,
            String requestedType,
            String requestedCountry,
            Integer limit,
            Integer offset,
            MatchContext context) {
        if (!consentService.hasActiveConsent(userId, "professional_matching")) {
            return new SearchResponse(null, Collections.emptyList());
        }

        SearchQuery parsedQuery = resolveParsedQuery(queryText, preParsedQuery);
        Optional<NodeType> typeFilter = resolveNodeTypeFilter(requestedType);
        if (requestedType != null && !requestedType.isBlank() && typeFilter.isEmpty()) {
            return new SearchResponse(parsedQuery, Collections.emptyList());
        }

        String embeddingText = parsedQuery.embeddingText();
        if (embeddingText == null || embeddingText.isBlank()) {
            embeddingText = queryText;
        }
        String queryTextForEmbedding = buildEmbeddingQueryText(embeddingText);
        float[] queryEmbedding = embeddingService.generateEmbedding(queryTextForEmbedding);
        if (queryEmbedding == null) {
            return new SearchResponse(parsedQuery, Collections.emptyList());
        }

        String countryFilter = requestedCountry != null && !requestedCountry.isBlank()
                ? requestedCountry
                : extractCountryFilter(parsedQuery);
        List<RawNodeCandidate> rawCandidates = unifiedVectorSearch(queryEmbedding, userId, parsedQuery, countryFilter);
        SkillLevelContext skillLevelContext = loadSkillLevelsContext(rawCandidates);
        MatchContext effectiveContext = context != null ? context : MatchContext.empty();
        List<ScoredCandidate> allScored = searchScoringEngine.scoreAndRank(
                rawCandidates, parsedQuery, skillLevelContext.levelsByNodeForScoring(), effectiveContext);
        if (typeFilter.isPresent()) {
            NodeType expectedType = typeFilter.get();
            allScored = allScored.stream()
                    .filter(sc -> sc.node.nodeType == expectedType)
                    .toList();
        }
        double minScore = config.search().minScore();
        List<ScoredCandidate> filtered = allScored.stream()
                .filter(sc -> sc.score >= minScore)
                .toList();
        List<ScoredCandidate> paged = SearchMatchingUtils.paginate(filtered, limit, offset, DEFAULT_RESULT_LIMIT);

        List<SearchResultItem> results = toResultItems(paged, skillLevelContext.levelsByNodeForResult());

        return new SearchResponse(parsedQuery, results);
    }

    private SearchQuery resolveParsedQuery(String queryText, SearchQuery preParsedQuery) {
        if (preParsedQuery != null) {
            return preParsedQuery;
        }
        SearchQueryParser parser = selectQueryParser().orElse(heuristicSearchQueryParser);
        return parser.parse(queryText)
                .or(() -> heuristicSearchQueryParser.parse(queryText))
                .orElse(new SearchQuery(null, null, "unknown", null, Collections.emptyList(), queryText, "unknown"));
    }

    private Optional<NodeType> resolveNodeTypeFilter(String requestedType) {
        if (requestedType == null || requestedType.isBlank()) {
            return Optional.empty();
        }
        if ("PEOPLE".equalsIgnoreCase(requestedType) || "PROFILE".equalsIgnoreCase(requestedType)) {
            return Optional.of(NodeType.USER);
        }
        try {
            return Optional.of(NodeType.valueOf(requestedType.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    // === Unified vector search on mesh_node (USER + JOB + COMMUNITY + ...) ===

    private List<RawNodeCandidate> unifiedVectorSearch(float[] queryEmbedding, UUID userId,
                                                       SearchQuery parsed, String countryFilter) {
        List<String> languages = (parsed.mustHave() != null && parsed.mustHave().languages() != null
                && !parsed.mustHave().languages().isEmpty())
                ? parsed.mustHave().languages() : null;

        List<Object[]> rows = searchRepository.unifiedVectorSearch(
                queryEmbedding, userId, countryFilter, languages, VECTOR_POOL_SIZE);

        List<RawNodeCandidate> candidates = new ArrayList<>();
        for (Object[] row : rows) {
            // Column mapping follows MeshNodeSearchRepository.unifiedVectorSearch select order:
            // [0]=id [1]=node_type [2]=title [3]=description [4]=tags [5]=country [6]=updated_at [7]=structured_data [8]=cosine_sim
            String sdRaw = row[7] != null ? row[7].toString() : null;
            Map<String, Object> sd = null;
            NodeType nodeType = SqlParsingUtils.parseEnum(NodeType.class, (String) row[1]);
            if (nodeType == NodeType.USER && sdRaw != null) {
                try {
                    sd = objectMapper.readValue(sdRaw, new TypeReference<>() {});
                } catch (Exception e) {
                    LOG.debugf("Failed to parse structured_data for nodeId=%s", row[0]);
                }
            }
            candidates.add(new RawNodeCandidate(
                    (UUID) row[0],
                    nodeType,
                    (String) row[2],
                    (String) row[3],
                    SqlParsingUtils.parseArray(row[4]),
                    (String) row[5],
                    SqlParsingUtils.toInstant(row[6]),
                    sd,
                    ((Number) row[8]).doubleValue()
            ));
        }
        return candidates;
    }

    // === Result conversion ===

    private List<SearchResultItem> toResultItems(
            List<ScoredCandidate> scored,
            Map<UUID, Map<String, Integer>> skillLevelsByNodeId) {
        List<SearchResultItem> items = new ArrayList<>();
        for (ScoredCandidate sc : scored) {
            RawNodeCandidate c = sc.node;
            if (c.nodeType == NodeType.USER) {
                List<String> roles = StringUtils.splitCommaSeparated(c.description);
                String city = sdString(c.structuredData, "city");
                String email = sdString(c.structuredData, "email");
                String slackHandle = sdString(c.structuredData, "slack_handle");
                String telegramHandle = sdString(c.structuredData, "telegram_handle");
                String mobilePhone = sdString(c.structuredData, "mobile_phone");
                String linkedinUrl = sdString(c.structuredData, "linkedin_url");
                List<String> languagesSpoken = sdListOrEmpty(c.structuredData, "languages_spoken");
                Seniority seniority = SqlParsingUtils.parseEnum(Seniority.class,
                        sdString(c.structuredData, "seniority"));
                WorkMode workMode = SqlParsingUtils.parseEnum(WorkMode.class,
                        sdString(c.structuredData, "work_mode"));
                EmploymentType employmentType = SqlParsingUtils.parseEnum(EmploymentType.class,
                        sdString(c.structuredData, "employment_type"));
                List<String> toolsAndTech = sdListOrEmpty(c.structuredData, "tools_and_tech");
                String avatarUrl = sdString(c.structuredData, "avatar_url");

                Map<String, Integer> skillLevels = skillLevelsByNodeId.get(c.nodeId);

                items.add(SearchResultItem.profile(
                        c.nodeId, StringUtils.round3(sc.score),
                        c.title, avatarUrl, roles, seniority,
                        c.tags, toolsAndTech,
                        languagesSpoken, c.country, city,
                        workMode, employmentType,
                        slackHandle, email, telegramHandle, mobilePhone, linkedinUrl,
                        sc.breakdown, skillLevels
                ));
            } else {
                items.add(SearchResultItem.node(
                        c.nodeId, StringUtils.round3(sc.score),
                        c.nodeType, c.title, c.description,
                        c.tags, c.country,
                        sc.breakdown
                ));
            }
        }
        return items;
    }

    // === Helpers ===

    private String buildEmbeddingQueryText(String embeddingText) {
        String prefix = config.search().queryPrefix().orElse("");
        if (prefix == null || prefix.isBlank()) {
            return embeddingText;
        }
        return prefix + embeddingText;
    }

    private String extractCountryFilter(SearchQuery parsed) {
        if (parsed == null || parsed.mustHave() == null || parsed.mustHave().location() == null) {
            return null;
        }
        for (String rawLocation : parsed.mustHave().location()) {
            String mapped = GeographyUtils.mapLocationToCountryCode(rawLocation);
            if (mapped != null) {
                return mapped;
            }
        }
        return null;
    }

    private SkillLevelContext loadSkillLevelsContext(List<RawNodeCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return SkillLevelContext.empty();
        }
        List<UUID> nodeIds = candidates.stream()
                .filter(c -> c.nodeType == NodeType.USER)
                .map(c -> c.nodeId)
                .toList();
        if (nodeIds.isEmpty()) {
            return SkillLevelContext.empty();
        }
        if (skillLevelResolutionService == null) {
            return SkillLevelContext.empty();
        }
        SkillLevelResolutionService.SkillLevels skillLevels = skillLevelResolutionService.resolveForNodeIds(nodeIds);
        if (skillLevels.levelsByNodeForResult().isEmpty() && skillLevels.levelsByNodeForScoring().isEmpty()) {
            return SkillLevelContext.empty();
        }
        return new SkillLevelContext(skillLevels.levelsByNodeForResult(), skillLevels.levelsByNodeForScoring());
    }

    private Optional<SearchQueryParser> selectQueryParser() {
        if (queryParsers == null || queryParsers.isEmpty()) {
            return Optional.empty();
        }
        Optional<SearchQueryParser> nonHeuristic = queryParsers.stream()
                .filter(Objects::nonNull)
                .filter(parser -> !(parser instanceof HeuristicSearchQueryParser))
                .findFirst();
        if (nonHeuristic.isPresent()) {
            return nonHeuristic;
        }
        return queryParsers.stream()
                .filter(Objects::nonNull)
                .findFirst();
    }

    // === Records ===

    private record SkillLevelContext(
            Map<UUID, Map<String, Integer>> levelsByNodeForResult,
            Map<UUID, Map<String, Short>> levelsByNodeForScoring
    ) {
        private static SkillLevelContext empty() {
            return new SkillLevelContext(Collections.emptyMap(), Collections.emptyMap());
        }
    }

    record RawNodeCandidate(
            UUID nodeId, NodeType nodeType, String title, String description,
            List<String> tags, String country,
            Instant updatedAt, Map<String, Object> structuredData,
            double cosineSim
    ) {}

    record ScoredCandidate(
            RawNodeCandidate node,
            SearchMatchBreakdown breakdown,
            double score
    ) {}

    public record MatchContext(
            String referenceCountry,
            WorkMode referenceWorkMode,
            EmploymentType referenceEmploymentType
    ) {
        static MatchContext empty() {
            return new MatchContext(null, null, null);
        }
    }

}
