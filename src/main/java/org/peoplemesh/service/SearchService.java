package org.peoplemesh.service;

import static org.peoplemesh.util.StructuredDataUtils.sdListOrEmpty;
import static org.peoplemesh.util.StructuredDataUtils.sdString;

import io.quarkus.arc.All;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.peoplemesh.repository.MeshNodeSearchRepository;
import org.jboss.logging.Logger;
import org.peoplemesh.config.AppConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.peoplemesh.domain.dto.*;
import org.peoplemesh.domain.enums.EmploymentType;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.enums.Seniority;
import org.peoplemesh.domain.enums.WorkMode;
import java.time.Instant;
import java.util.regex.Pattern;
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
    private static final int RESULT_LIMIT = 20;
    private static final Pattern TOKEN_TRIM_PATTERN = Pattern.compile("^[^\\p{Alnum}]+|[^\\p{Alnum}]+$");

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

    public SearchResponse search(UUID userId, String queryText, String countryFilter) {
        if (!consentService.hasActiveConsent(userId, "professional_matching")) {
            return new SearchResponse(null, Collections.emptyList());
        }

        SearchQueryParser parser = selectQueryParser().orElse(null);
        ParsedSearchQuery parsedQuery;
        if (parser != null) {
            parsedQuery = parser.parse(queryText).orElse(fallbackParse(queryText));
        } else {
            parsedQuery = fallbackParse(queryText);
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

        List<RawNodeCandidate> rawCandidates = unifiedVectorSearch(queryEmbedding, userId,
                parsedQuery, countryFilter);
        SkillLevelContext skillLevelContext = loadSkillLevelsContext(rawCandidates);
        List<ScoredCandidate> allScored = unifiedScore(rawCandidates, parsedQuery, skillLevelContext.levelsByNodeForScoring());
        allScored = rerank(allScored, parsedQuery);
        double minScore = config.search().minScore();
        allScored = allScored.stream()
                .filter(sc -> sc.score >= minScore)
                .limit(RESULT_LIMIT).toList();

        List<SearchResultItem> results = toResultItems(allScored, skillLevelContext.levelsByNodeForResult());

        return new SearchResponse(parsedQuery, results);
    }

    // === Unified vector search on mesh_node (USER + JOB + COMMUNITY + ...) ===

    private List<RawNodeCandidate> unifiedVectorSearch(float[] queryEmbedding, UUID userId,
                                                       ParsedSearchQuery parsed, String countryFilter) {
        List<String> languages = (parsed.mustHave() != null && parsed.mustHave().languages() != null
                && !parsed.mustHave().languages().isEmpty())
                ? parsed.mustHave().languages() : null;

        List<Object[]> rows = searchRepository.unifiedVectorSearch(
                queryEmbedding, userId, countryFilter, languages, VECTOR_POOL_SIZE);

        List<RawNodeCandidate> candidates = new ArrayList<>();
        for (Object[] row : rows) {
            String sdRaw = row[7] != null ? row[7].toString() : null;
            Map<String, Object> sd = null;
            NodeType nodeType = MatchingUtils.parseEnum(NodeType.class, (String) row[1]);
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
                    MatchingUtils.parseArray(row[4]),
                    (String) row[5],
                    MatchingUtils.toInstant(row[6]),
                    sd,
                    ((Number) row[8]).doubleValue()
            ));
        }
        return candidates;
    }

    // === Unified scoring ===

    private List<ScoredCandidate> unifiedScore(
            List<RawNodeCandidate> candidates,
            ParsedSearchQuery parsed,
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
                scored.add(scoreGenericNode(c, keywords, mustHaveSkills, negativeSkills));
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
        candidateSkills.addAll(toolsAndTech);

        List<String> matchedMust = MatchingUtils.intersectCaseInsensitive(mustHaveSkills, candidateSkills);
        Set<String> matchedMustNormalized = matchedMust.stream()
                .map(SearchService::normalizeTerm)
                .collect(Collectors.toSet());
        List<String> missingMust = mustHaveSkills.stream()
                .filter(s -> !matchedMustNormalized.contains(normalizeTerm(s)))
                .toList();
        double mustHaveCoverage = mustHaveSkills.isEmpty() ? 1.0
                : (double) matchedMust.size() / mustHaveSkills.size();

        if (mustHaveWithLevel != null && !mustHaveWithLevel.isEmpty()) {
            mustHaveCoverage = MatchingUtils.computeLevelAwareCoverage(mustHaveWithLevel, candidateSkills, cachedLevelsBySkillName);
        }

        List<String> matchedNice = MatchingUtils.intersectCaseInsensitive(niceToHaveSkills, candidateSkills);
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
                MatchingUtils.round3(c.cosineSim),
                MatchingUtils.round3(mustHaveCoverage),
                MatchingUtils.round3(niceToHaveBonus),
                MatchingUtils.round3(languageScore),
                MatchingUtils.round3(industryScore),
                MatchingUtils.round3(rawScore),
                matchedMust,
                matchedNice,
                missingMust,
                reasonCodes
        );

        return new ScoredCandidate(c, breakdown, rawScore);
    }

    private ScoredCandidate scoreGenericNode(RawNodeCandidate c,
                                              List<String> keywords, List<String> mustHaveSkills,
                                              List<String> negativeSkills) {
        List<String> allSearchTerms = new ArrayList<>(keywords);
        allSearchTerms.addAll(mustHaveSkills);

        List<String> nodeTags = c.tags != null ? c.tags : Collections.emptyList();
        List<String> nodeText = new ArrayList<>(nodeTags);
        if (c.title != null) nodeText.add(c.title);
        if (c.description != null) nodeText.add(c.description);

        List<String> matchedTerms = MatchingUtils.intersectCaseInsensitive(allSearchTerms, nodeText);
        double tagCoverage = allSearchTerms.isEmpty() ? 0.0
                : (double) matchedTerms.size() / allSearchTerms.size();

        double rawScore = c.cosineSim * 0.70 + tagCoverage * 0.30;
        if (!mustHaveSkills.isEmpty() && matchedTerms.isEmpty()) {
            rawScore *= 0.2;
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
        if (!matchedTerms.isEmpty()) reasonCodes.add("TAG_MATCH");
        reasonCodes.add("NODE_" + c.nodeType.name());

        SearchMatchBreakdown breakdown = new SearchMatchBreakdown(
                MatchingUtils.round3(c.cosineSim),
                MatchingUtils.round3(tagCoverage),
                0, 0, 0,
                MatchingUtils.round3(rawScore),
                matchedTerms,
                Collections.emptyList(),
                Collections.emptyList(),
                reasonCodes
        );

        return new ScoredCandidate(c, breakdown, rawScore);
    }

    private List<ScoredCandidate> rerank(List<ScoredCandidate> candidates, ParsedSearchQuery parsed) {
        String targetSeniority = parsed.seniority();
        Seniority target = (targetSeniority == null || "unknown".equalsIgnoreCase(targetSeniority))
                ? null
                : MatchingUtils.parseEnum(Seniority.class, targetSeniority.toUpperCase());
        if (target == null) {
            return candidates;
        }

        List<ScoredCandidate> reranked = new ArrayList<>(candidates);
        for (int i = 0; i < reranked.size(); i++) {
            ScoredCandidate sc = reranked.get(i);
            if (sc.node.nodeType != NodeType.USER) continue;
            double adjusted = sc.score;

            String seniorityStr = sdString(sc.node.structuredData, "seniority");
            Seniority candidateSeniority = MatchingUtils.parseEnum(Seniority.class, seniorityStr);
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
                List<String> languagesSpoken = sdListOrEmpty(c.structuredData, "languages_spoken");
                Seniority seniority = MatchingUtils.parseEnum(Seniority.class,
                        sdString(c.structuredData, "seniority"));
                WorkMode workMode = MatchingUtils.parseEnum(WorkMode.class,
                        sdString(c.structuredData, "work_mode"));
                EmploymentType employmentType = MatchingUtils.parseEnum(EmploymentType.class,
                        sdString(c.structuredData, "employment_type"));
                List<String> toolsAndTech = sdListOrEmpty(c.structuredData, "tools_and_tech");
                String avatarUrl = sdString(c.structuredData, "avatar_url");

                Map<String, Integer> skillLevels = skillLevelsByNodeId.get(c.nodeId);

                items.add(SearchResultItem.profile(
                        c.nodeId, MatchingUtils.round3(sc.score),
                        c.title, avatarUrl, roles, seniority,
                        c.tags, toolsAndTech,
                        languagesSpoken, c.country, city,
                        workMode, employmentType,
                        slackHandle, email, telegramHandle, mobilePhone,
                        sc.breakdown, skillLevels
                ));
            } else {
                items.add(SearchResultItem.node(
                        c.nodeId, MatchingUtils.round3(sc.score),
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
        return MatchingUtils.splitCommaSeparated(plain);
    }

    private static String normalizeTerm(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.toLowerCase(Locale.ROOT).trim();
    }

    private String buildEmbeddingQueryText(String embeddingText) {
        String prefix = config.search().queryPrefix().orElse("");
        if (prefix == null || prefix.isBlank()) {
            return embeddingText;
        }
        return prefix + embeddingText;
    }

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "and", "or", "but", "in", "on", "at", "to", "for",
            "of", "with", "by", "from", "as", "is", "was", "are", "were", "be",
            "been", "being", "have", "has", "had", "do", "does", "did", "will",
            "would", "could", "should", "may", "might", "can", "shall", "must",
            "need", "not", "no", "nor", "so", "if", "then", "than", "that",
            "this", "these", "those", "it", "its", "i", "we", "you", "he", "she",
            "they", "me", "him", "her", "us", "them", "my", "your", "his", "our",
            "their", "who", "whom", "which", "what", "where", "when", "how", "why",
            "all", "each", "every", "both", "few", "more", "most", "some", "any",
            "such", "only", "very", "just", "also", "about", "up", "out", "into",
            "over", "after", "before", "between", "under", "above", "below",
            "looking", "find", "search", "want", "needed", "required",
            "experience", "experienced", "expertise", "expert", "proficient",
            "knowledge", "familiar", "background", "developer", "engineer",
            "consultant", "specialist", "professional", "person", "someone",
            "anybody", "people", "team", "work", "working", "role"
    );

    private static final Map<String, String> LANGUAGE_ALIASES = Map.ofEntries(
            Map.entry("english", "English"),
            Map.entry("italian", "Italian"),
            Map.entry("italiano", "Italian"),
            Map.entry("french", "French"),
            Map.entry("francese", "French"),
            Map.entry("spanish", "Spanish"),
            Map.entry("spagnolo", "Spanish"),
            Map.entry("german", "German"),
            Map.entry("tedesco", "German"),
            Map.entry("portuguese", "Portuguese"),
            Map.entry("portoghese", "Portuguese"),
            Map.entry("dutch", "Dutch"),
            Map.entry("polish", "Polish"),
            Map.entry("swedish", "Swedish"),
            Map.entry("norwegian", "Norwegian"),
            Map.entry("danish", "Danish"),
            Map.entry("finnish", "Finnish"),
            Map.entry("romanian", "Romanian"),
            Map.entry("russian", "Russian"),
            Map.entry("ukrainian", "Ukrainian"),
            Map.entry("arabic", "Arabic"),
            Map.entry("hindi", "Hindi"),
            Map.entry("chinese", "Chinese"),
            Map.entry("mandarin", "Chinese"),
            Map.entry("japanese", "Japanese"),
            Map.entry("korean", "Korean"),
            Map.entry("turkish", "Turkish")
    );

    private ParsedSearchQuery fallbackParse(String queryText) {
        List<String> words = new ArrayList<>();
        Set<String> languages = new LinkedHashSet<>();
        for (String rawToken : queryText.split("\\s+")) {
            String token = TOKEN_TRIM_PATTERN.matcher(rawToken).replaceAll("").trim();
            if (token.length() <= 1) {
                continue;
            }
            String normalized = token.toLowerCase(Locale.ROOT);
            if (STOP_WORDS.contains(normalized)) {
                continue;
            }
            String canonicalLanguage = LANGUAGE_ALIASES.get(normalized);
            if (canonicalLanguage != null) {
                languages.add(canonicalLanguage);
                continue;
            }
            words.add(token);
        }
        return new ParsedSearchQuery(
                new ParsedSearchQuery.MustHaveFilters(words, null, Collections.emptyList(),
                        new ArrayList<>(languages), Collections.emptyList(), Collections.emptyList()),
                new ParsedSearchQuery.NiceToHaveFilters(Collections.emptyList(), null,
                        Collections.emptyList(), Collections.emptyList()),
                "unknown", null, words, queryText
        );
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
