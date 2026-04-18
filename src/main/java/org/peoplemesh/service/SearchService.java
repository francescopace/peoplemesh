package org.peoplemesh.service;

import static org.peoplemesh.util.StructuredDataUtils.sdListOrEmpty;
import static org.peoplemesh.util.StructuredDataUtils.sdString;

import io.quarkus.arc.All;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.peoplemesh.repository.MeshNodeSearchRepository;
import org.jboss.logging.Logger;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.util.GeographyUtils;
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
import java.util.stream.Collectors;

@ApplicationScoped
public class SearchService {

    private static final Logger LOG = Logger.getLogger(SearchService.class);

    private static final double W_EMBEDDING = 0.50;
    private static final double W_MUST_HAVE = 0.25;
    private static final double W_NICE_TO_HAVE = 0.10;
    private static final double W_LANGUAGE = 0.10;
    private static final double W_INDUSTRY = 0.05;

    private static final int VECTOR_POOL_SIZE = 100;
    private static final int DEFAULT_RESULT_LIMIT = 20;
    private static final HeuristicSearchQueryParser FALLBACK_QUERY_PARSER = new HeuristicSearchQueryParser();

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
    SkillLevelResolutionService skillLevelResolutionService;

    @Inject
    SemanticSkillMatcher semanticSkillMatcher;

    public SearchResponse search(UUID userId, String queryText) {
        return search(userId, queryText, null, null, null, null, null);
    }

    public SearchResponse search(UUID userId, String queryText, Integer limit, Integer offset) {
        return search(userId, queryText, null, null, null, limit, offset);
    }

    public SearchResponse search(
            UUID userId,
            String queryText,
            SearchQuery preParsedQuery,
            String requestedType,
            String requestedCountry,
            Integer limit,
            Integer offset) {
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
        List<ScoredCandidate> allScored = unifiedScore(rawCandidates, parsedQuery, skillLevelContext.levelsByNodeForScoring());
        allScored = rerank(allScored, parsedQuery);
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
        List<ScoredCandidate> paged = applyPagination(filtered, limit, offset);

        List<SearchResultItem> results = toResultItems(paged, skillLevelContext.levelsByNodeForResult());

        return new SearchResponse(parsedQuery, results);
    }

    private SearchQuery resolveParsedQuery(String queryText, SearchQuery preParsedQuery) {
        if (preParsedQuery != null) {
            return preParsedQuery;
        }
        SearchQueryParser parser = selectQueryParser().orElse(null);
        SearchQueryParser effectiveParser = parser != null ? parser : FALLBACK_QUERY_PARSER;
        return effectiveParser.parse(queryText)
                .or(() -> FALLBACK_QUERY_PARSER.parse(queryText))
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

    // === Unified scoring ===

    private List<ScoredCandidate> unifiedScore(
            List<RawNodeCandidate> candidates,
            SearchQuery parsed,
            Map<UUID, Map<String, Short>> levelCacheByNode) {
        List<String> mustHaveSkills = parsed.mustHave() != null && parsed.mustHave().skills() != null
                ? parsed.mustHave().skills() : Collections.emptyList();
        List<String> niceToHaveSkills = parsed.niceToHave() != null && parsed.niceToHave().skills() != null
                ? parsed.niceToHave().skills() : Collections.emptyList();
        List<String> mustHaveLanguages = parsed.mustHave() != null && parsed.mustHave().languages() != null
                ? parsed.mustHave().languages() : Collections.emptyList();
        List<String> mustHaveIndustries = parsed.mustHave() != null && parsed.mustHave().industries() != null
                ? parsed.mustHave().industries() : Collections.emptyList();
        List<String> niceToHaveIndustries = parsed.niceToHave() != null && parsed.niceToHave().industries() != null
                ? parsed.niceToHave().industries() : Collections.emptyList();
        List<String> keywords = parsed.keywords() != null ? parsed.keywords() : Collections.emptyList();
        List<String> negativeSkills = parsed.negativeFilters() != null && parsed.negativeFilters().skills() != null
                ? parsed.negativeFilters().skills() : Collections.emptyList();

        List<SkillWithLevel> mustHaveWithLevel = parsed.mustHave() != null
                ? parsed.mustHave().skillsWithLevel() : null;
        List<SkillWithLevel> niceToHaveWithLevel = parsed.niceToHave() != null
                ? parsed.niceToHave().skillsWithLevel() : null;

        List<ScoredCandidate> scored = new ArrayList<>();

        for (RawNodeCandidate c : candidates) {
            if (c.nodeType == NodeType.USER) {
                scored.add(scoreUserNode(c, mustHaveSkills, niceToHaveSkills,
                        mustHaveLanguages, mustHaveIndustries, niceToHaveIndustries,
                        mustHaveWithLevel, niceToHaveWithLevel, levelCacheByNode.get(c.nodeId), negativeSkills));
            } else {
                scored.add(scoreGenericNode(c, keywords, mustHaveSkills, niceToHaveSkills, negativeSkills));
            }
        }

        scored.sort(Comparator.comparingDouble(ScoredCandidate::score).reversed());
        return scored;
    }

    private ScoredCandidate scoreUserNode(RawNodeCandidate c,
                                           List<String> mustHaveSkills, List<String> niceToHaveSkills,
                                           List<String> mustHaveLanguages,
                                           List<String> mustHaveIndustries, List<String> niceToHaveIndustries,
                                           List<SkillWithLevel> mustHaveWithLevel,
                                           List<SkillWithLevel> niceToHaveWithLevel,
                                           Map<String, Short> cachedLevelsBySkillName,
                                           List<String> negativeSkills) {
        List<String> candidateSkills = c.tags != null ? new ArrayList<>(c.tags) : new ArrayList<>();
        List<String> toolsAndTech = sdListOrEmpty(c.structuredData, "tools_and_tech");
        List<String> softSkills = sdListOrEmpty(c.structuredData, "skills_soft");
        candidateSkills.addAll(toolsAndTech);
        candidateSkills.addAll(softSkills);
        if (cachedLevelsBySkillName != null && !cachedLevelsBySkillName.isEmpty()) {
            candidateSkills.addAll(cachedLevelsBySkillName.keySet());
        }
        candidateSkills = deduplicateTerms(candidateSkills);

        double skillMatchThreshold = config.search().skillMatchThreshold();
        List<String> matchedMust = semanticSkillMatcher.matchSkills(mustHaveSkills, candidateSkills, skillMatchThreshold).stream()
                .map(SemanticSkillMatcher.SemanticMatch::querySkill)
                .distinct()
                .toList();
        Set<String> matchedMustNormalized = matchedMust.stream()
                .map(MatchingUtils::normalizeTerm)
                .collect(Collectors.toSet());
        List<String> missingMust = mustHaveSkills.stream()
                .filter(s -> !matchedMustNormalized.contains(MatchingUtils.normalizeTerm(s)))
                .toList();
        double mustHaveCoverage = mustHaveSkills.isEmpty() ? 1.0
                : (double) matchedMust.size() / mustHaveSkills.size();

        if (mustHaveWithLevel != null && !mustHaveWithLevel.isEmpty()) {
            mustHaveCoverage = MatchingUtils.computeLevelAwareCoverage(mustHaveWithLevel, candidateSkills, cachedLevelsBySkillName);
        }

        List<String> matchedNice = semanticSkillMatcher.matchSkills(niceToHaveSkills, candidateSkills, skillMatchThreshold).stream()
                .map(SemanticSkillMatcher.SemanticMatch::querySkill)
                .distinct()
                .toList();
        double niceToHaveBonus = niceToHaveSkills.isEmpty() ? 0.0
                : (double) matchedNice.size() / niceToHaveSkills.size();

        if (niceToHaveWithLevel != null && !niceToHaveWithLevel.isEmpty()) {
            double levelBonus = MatchingUtils.computeLevelAwareCoverage(niceToHaveWithLevel, candidateSkills, cachedLevelsBySkillName);
            niceToHaveBonus = Math.max(niceToHaveBonus, levelBonus);
        }

        List<String> languagesSpoken = sdListOrEmpty(c.structuredData, "languages_spoken");
        double languageScore = mustHaveLanguages.isEmpty() ? 1.0
                : MatchingUtils.intersectCaseInsensitive(mustHaveLanguages, languagesSpoken).isEmpty() ? 0.0 : 1.0;

        List<String> allIndustries = MatchingUtils.combineLists(mustHaveIndustries, niceToHaveIndustries);
        double industryScore = 0.0;
        if (!allIndustries.isEmpty()) {
            List<String> candidateIndustries = sdListOrEmpty(c.structuredData, "industries");
            if (!candidateIndustries.isEmpty()) {
                List<String> matched = MatchingUtils.intersectCaseInsensitive(allIndustries, candidateIndustries);
                industryScore = (double) matched.size() / allIndustries.size();
            }
        }

        double rawScore = c.cosineSim * W_EMBEDDING
                + mustHaveCoverage * W_MUST_HAVE
                + niceToHaveBonus * W_NICE_TO_HAVE
                + languageScore * W_LANGUAGE
                + industryScore * W_INDUSTRY;

        if (!missingMust.isEmpty() && !mustHaveSkills.isEmpty()) {
            double missingRatio = (double) missingMust.size() / mustHaveSkills.size();
            rawScore *= Math.max(0.0, 1.0 - missingRatio * 0.8);
        }
        if (!mustHaveSkills.isEmpty() && mustHaveCoverage == 0.0) {
            // Hard precision gate: if no must-have matches, suppress semantic-only false positives.
            rawScore = 0.0;
        }

        List<String> matchedNegative = MatchingUtils.intersectCaseInsensitive(negativeSkills, candidateSkills);
        if (!matchedNegative.isEmpty() && !negativeSkills.isEmpty()) {
            double matchedNegativeRatio = (double) matchedNegative.size() / negativeSkills.size();
            rawScore *= Math.max(0.0, 1.0 - matchedNegativeRatio * 0.8);
        }

        List<String> reasonCodes = new ArrayList<>();
        if (c.cosineSim >= 0.65) reasonCodes.add("SEMANTIC_SIMILARITY");
        if (!matchedMust.isEmpty()) reasonCodes.add("MUST_HAVE_SKILLS");
        if (!matchedNice.isEmpty()) reasonCodes.add("NICE_TO_HAVE_SKILLS");
        if (languageScore > 0) reasonCodes.add("LANGUAGE_MATCH");
        if (industryScore > 0) reasonCodes.add("INDUSTRY_MATCH");

        SearchMatchBreakdown breakdown = new SearchMatchBreakdown(
                StringUtils.round3(c.cosineSim),
                StringUtils.round3(mustHaveCoverage),
                StringUtils.round3(niceToHaveBonus),
                StringUtils.round3(languageScore),
                StringUtils.round3(industryScore),
                StringUtils.round3(rawScore),
                matchedMust,
                matchedNice,
                missingMust,
                reasonCodes
        );

        return new ScoredCandidate(c, breakdown, rawScore);
    }

    private ScoredCandidate scoreGenericNode(RawNodeCandidate c,
                                             List<String> keywords,
                                             List<String> mustHaveSkills,
                                             List<String> niceToHaveSkills,
                                             List<String> negativeSkills) {
        List<String> nodeTags = c.tags != null ? c.tags : Collections.emptyList();
        double skillMatchThreshold = config.search().skillMatchThreshold();
        List<String> matchedMustSemantic = semanticSkillMatcher.matchSkills(mustHaveSkills, nodeTags, skillMatchThreshold).stream()
                .map(SemanticSkillMatcher.SemanticMatch::querySkill)
                .distinct()
                .toList();
        List<String> matchedNice = semanticSkillMatcher.matchSkills(niceToHaveSkills, nodeTags, skillMatchThreshold).stream()
                .map(SemanticSkillMatcher.SemanticMatch::querySkill)
                .distinct()
                .toList();

        List<String> nodeText = new ArrayList<>(nodeTags);
        if (c.title != null) nodeText.add(c.title);
        if (c.description != null) nodeText.add(c.description);

        List<String> matchedKeywordTerms = MatchingUtils.intersectCaseInsensitive(keywords, nodeText);
        List<String> matchedMustText = MatchingUtils.intersectCaseInsensitive(mustHaveSkills, nodeText);
        List<String> matchedMust = deduplicateTerms(
                MatchingUtils.combineLists(matchedMustSemantic, matchedMustText));
        Set<String> matchedMustNormalized = matchedMust.stream()
                .map(MatchingUtils::normalizeTerm)
                .collect(Collectors.toSet());
        List<String> missingMust = mustHaveSkills.stream()
                .filter(s -> !matchedMustNormalized.contains(MatchingUtils.normalizeTerm(s)))
                .toList();

        double mustHaveCoverage = mustHaveSkills.isEmpty() ? 1.0
                : (double) matchedMust.size() / mustHaveSkills.size();
        double niceToHaveBonus = niceToHaveSkills.isEmpty() ? 0.0
                : (double) matchedNice.size() / niceToHaveSkills.size();
        double keywordScore = keywords.isEmpty() ? 0.0
                : (double) matchedKeywordTerms.size() / keywords.size();

        double rawScore = c.cosineSim * 0.65
                + mustHaveCoverage * 0.20
                + niceToHaveBonus * 0.10
                + keywordScore * 0.05;

        if (!mustHaveSkills.isEmpty() && !missingMust.isEmpty()) {
            double missingRatio = (double) missingMust.size() / mustHaveSkills.size();
            rawScore *= Math.max(0.0, 1.0 - missingRatio * 0.8);
        }
        if (!mustHaveSkills.isEmpty() && mustHaveCoverage == 0.0) {
            rawScore = 0.0;
        }
        if (!negativeSkills.isEmpty()) {
            List<String> negativeMatches = MatchingUtils.intersectCaseInsensitive(negativeSkills, nodeText);
            if (!negativeMatches.isEmpty()) {
                double negativeRatio = (double) negativeMatches.size() / negativeSkills.size();
                rawScore *= Math.max(0.0, 1.0 - negativeRatio * 0.8);
            }
        }

        List<String> reasonCodes = new ArrayList<>();
        if (c.cosineSim >= 0.60) reasonCodes.add("SEMANTIC_SIMILARITY");
        if (!matchedMust.isEmpty()) reasonCodes.add("MUST_HAVE_SKILLS");
        if (!matchedNice.isEmpty()) reasonCodes.add("NICE_TO_HAVE_SKILLS");
        if (!matchedKeywordTerms.isEmpty()) reasonCodes.add("KEYWORD_MATCH");
        reasonCodes.add("NODE_" + c.nodeType.name());

        SearchMatchBreakdown breakdown = new SearchMatchBreakdown(
                StringUtils.round3(c.cosineSim),
                StringUtils.round3(mustHaveCoverage),
                StringUtils.round3(niceToHaveBonus),
                0, 0,
                StringUtils.round3(rawScore),
                matchedMust,
                matchedNice,
                missingMust,
                reasonCodes
        );

        return new ScoredCandidate(c, breakdown, rawScore);
    }

    private List<ScoredCandidate> rerank(List<ScoredCandidate> candidates, SearchQuery parsed) {
        String targetSeniority = parsed.seniority();
        Seniority target = (targetSeniority == null || "unknown".equalsIgnoreCase(targetSeniority))
                ? null
                : SqlParsingUtils.parseEnum(Seniority.class, targetSeniority.toUpperCase());
        if (target == null) {
            return candidates;
        }

        List<ScoredCandidate> reranked = new ArrayList<>(candidates);
        for (int i = 0; i < reranked.size(); i++) {
            ScoredCandidate sc = reranked.get(i);
            if (sc.node.nodeType != NodeType.USER) continue;
            double adjusted = sc.score;

            String seniorityStr = sdString(sc.node.structuredData, "seniority");
            Seniority candidateSeniority = SqlParsingUtils.parseEnum(Seniority.class, seniorityStr);
            if (candidateSeniority == target) {
                adjusted *= 1.05;
            } else if (candidateSeniority != null) {
                adjusted *= 0.95;
            }

            reranked.set(i, new ScoredCandidate(sc.node, sc.breakdown, adjusted));
        }

        reranked.sort(Comparator.comparingDouble(ScoredCandidate::score).reversed());
        return reranked;
    }

    // === Result conversion ===

    private List<SearchResultItem> toResultItems(
            List<ScoredCandidate> scored,
            Map<UUID, Map<String, Integer>> skillLevelsByNodeId) {
        List<SearchResultItem> items = new ArrayList<>();
        for (ScoredCandidate sc : scored) {
            RawNodeCandidate c = sc.node;
            if (c.nodeType == NodeType.USER) {
                List<String> roles = splitCommaSeparated(c.description);
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

    private static List<String> splitCommaSeparated(String plain) {
        return StringUtils.splitCommaSeparated(plain);
    }

    private static List<String> deduplicateTerms(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> seen = new HashSet<>();
        List<String> deduplicated = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String key = MatchingUtils.normalizeTerm(value);
            if (seen.add(key)) {
                deduplicated.add(value.trim());
            }
        }
        return deduplicated;
    }

    private List<ScoredCandidate> applyPagination(List<ScoredCandidate> all, Integer requestedLimit, Integer requestedOffset) {
        int safeOffset = requestedOffset != null ? Math.max(0, requestedOffset) : 0;
        int safeLimit = requestedLimit != null ? Math.max(1, requestedLimit) : DEFAULT_RESULT_LIMIT;
        if (safeOffset >= all.size()) {
            return Collections.emptyList();
        }
        int toIndex = Math.min(all.size(), safeOffset + safeLimit);
        return all.subList(safeOffset, toIndex);
    }

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

    private record RawNodeCandidate(
            UUID nodeId, NodeType nodeType, String title, String description,
            List<String> tags, String country,
            Instant updatedAt, Map<String, Object> structuredData,
            double cosineSim
    ) {}

    private record ScoredCandidate(
            RawNodeCandidate node,
            SearchMatchBreakdown breakdown,
            double score
    ) {}

}
