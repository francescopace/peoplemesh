package org.peoplemesh.service;

import static org.peoplemesh.util.StructuredDataUtils.sdListOrEmpty;
import static org.peoplemesh.util.StructuredDataUtils.sdString;

import io.quarkus.arc.All;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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
import java.util.*;

@ApplicationScoped
public class SearchService {

    private static final int DEFAULT_RESULT_LIMIT = 10;

    @Inject @All
    List<SearchQueryParser> queryParsers;

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
        return search(userId, queryText, preParsedQuery, requestedType, requestedCountry, limit, offset, context, null);
    }

    public SearchResponse search(
            UUID userId,
            String queryText,
            SearchQuery preParsedQuery,
            String requestedType,
            String requestedCountry,
            Integer limit,
            Integer offset,
            MatchContext context,
            SearchOptions tuning) {
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
        float[] queryEmbedding = embeddingService.generateEmbedding(embeddingText);
        if (queryEmbedding == null) {
            return new SearchResponse(parsedQuery, Collections.emptyList());
        }

        String countryFilter = requestedCountry != null && !requestedCountry.isBlank()
                ? requestedCountry
                : extractCountryFilter(parsedQuery);
        List<RawNodeCandidate> rawCandidates = unifiedVectorSearch(
                queryEmbedding,
                userId,
                parsedQuery,
                countryFilter,
                typeFilter.orElse(null));
        MatchContext effectiveContext = context != null ? context : MatchContext.empty();
        List<ScoredCandidate> allScored = searchScoringEngine.scoreAndRank(
                rawCandidates, parsedQuery, effectiveContext, tuning);
        if (typeFilter.isPresent()) {
            NodeType expectedType = typeFilter.get();
            allScored = allScored.stream()
                    .filter(sc -> sc.node().nodeType() == expectedType)
                    .toList();
        }
        List<ScoredCandidate> paged = SearchMatchingUtils.paginate(allScored, limit, offset, DEFAULT_RESULT_LIMIT);

        List<SearchResultItem> results = toResultItems(paged);

        return new SearchResponse(parsedQuery, results);
    }

    private SearchQuery resolveParsedQuery(String queryText, SearchQuery preParsedQuery) {
        if (preParsedQuery != null) {
            return preParsedQuery;
        }
        SearchQueryParser parser = selectQueryParser()
                .orElseThrow(() -> new IllegalStateException("No search query parser configured"));
        return parser.parse(queryText)
                .orElseThrow(() -> new IllegalStateException("Search query parsing failed"));
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
                                                       SearchQuery parsed, String countryFilter,
                                                       NodeType targetType) {
        List<String> languages = (parsed.mustHave() != null && parsed.mustHave().languages() != null
                && !parsed.mustHave().languages().isEmpty())
                ? parsed.mustHave().languages() : null;
        int poolSize = config.search().candidatePoolSize();

        List<MeshNodeSearchRepository.UnifiedSearchRow> rows = searchRepository
                .unifiedVectorSearch(queryEmbedding, userId, countryFilter, languages, targetType, poolSize);

        List<RawNodeCandidate> candidates = new ArrayList<>();
        for (MeshNodeSearchRepository.UnifiedSearchRow row : rows) {
            String sdRaw = row.structuredDataJson();
            Map<String, Object> sd = null;
            NodeType nodeType = SqlParsingUtils.parseEnum(NodeType.class, row.nodeType());
            if (nodeType == NodeType.USER && sdRaw != null) {
                try {
                    sd = objectMapper.readValue(sdRaw, new TypeReference<>() {});
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to parse structured_data for nodeId=" + row.nodeId(), e);
                }
            }
            candidates.add(new RawNodeCandidate(
                    row.nodeId(),
                    nodeType,
                    row.title(),
                    row.description(),
                    row.tags(),
                    row.country(),
                    row.updatedAt(),
                    sd,
                    row.cosineSim()
            ));
        }
        return candidates;
    }

    // === Result conversion ===

    private List<SearchResultItem> toResultItems(List<ScoredCandidate> scored) {
        List<SearchResultItem> items = new ArrayList<>();
        for (ScoredCandidate sc : scored) {
            RawNodeCandidate c = sc.node();
            if (c.nodeType() == NodeType.USER) {
                List<String> roles = StringUtils.splitCommaSeparated(c.description());
                String city = sdString(c.structuredData(), "city");
                String email = sdString(c.structuredData(), "email");
                String slackHandle = sdString(c.structuredData(), "slack_handle");
                String telegramHandle = sdString(c.structuredData(), "telegram_handle");
                String mobilePhone = sdString(c.structuredData(), "mobile_phone");
                String linkedinUrl = sdString(c.structuredData(), "linkedin_url");
                List<String> languagesSpoken = sdListOrEmpty(c.structuredData(), "languages_spoken");
                Seniority seniority = SqlParsingUtils.parseEnum(Seniority.class,
                        sdString(c.structuredData(), "seniority"));
                WorkMode workMode = SqlParsingUtils.parseEnum(WorkMode.class,
                        sdString(c.structuredData(), "work_mode"));
                EmploymentType employmentType = SqlParsingUtils.parseEnum(EmploymentType.class,
                        sdString(c.structuredData(), "employment_type"));
                List<String> toolsAndTech = sdListOrEmpty(c.structuredData(), "tools_and_tech");
                String avatarUrl = sdString(c.structuredData(), "avatar_url");

                items.add(SearchResultItem.profile(
                        c.nodeId(), StringUtils.round3(sc.score()),
                        c.title(), avatarUrl, roles, seniority,
                        c.tags(), toolsAndTech,
                        languagesSpoken, c.country(), city,
                        workMode, employmentType,
                        slackHandle, email, telegramHandle, mobilePhone, linkedinUrl,
                        sc.breakdown(), null
                ));
            } else {
                items.add(SearchResultItem.node(
                        c.nodeId(), StringUtils.round3(sc.score()),
                        c.nodeType(), c.title(), c.description(),
                        c.tags(), c.country(),
                        sc.breakdown()
                ));
            }
        }
        return items;
    }

    // === Helpers ===

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

    private Optional<SearchQueryParser> selectQueryParser() {
        if (queryParsers == null || queryParsers.isEmpty()) {
            return Optional.empty();
        }
        return queryParsers.stream()
                .filter(Objects::nonNull)
                .findFirst();
    }

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
