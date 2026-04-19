package org.peoplemesh.service;

import static org.peoplemesh.util.StructuredDataUtils.sdListOrEmpty;
import static org.peoplemesh.util.StructuredDataUtils.sdString;
import static org.peoplemesh.util.StructuredDataUtils.sdStringListOrEmpty;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.domain.dto.SearchMatchBreakdown;
import org.peoplemesh.domain.dto.SearchQuery;
import org.peoplemesh.domain.dto.SkillWithLevel;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.enums.Seniority;
import org.peoplemesh.util.GeographyUtils;
import org.peoplemesh.util.MatchingUtils;
import org.peoplemesh.util.SearchMatchingUtils;
import org.peoplemesh.util.SqlParsingUtils;
import org.peoplemesh.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class SearchScoringEngine {

    private static final double W_EMBEDDING = 0.50;
    private static final double W_MUST_HAVE = 0.25;
    private static final double W_NICE_TO_HAVE = 0.10;
    private static final double W_LANGUAGE = 0.10;
    private static final double W_INDUSTRY = 0.05;
    private static final double W_GEOGRAPHY = 0.05;

    private static final double W_GENERIC_EMBEDDING = 0.65;
    private static final double W_GENERIC_MUST_HAVE = 0.20;
    private static final double W_GENERIC_NICE_TO_HAVE = 0.10;
    private static final double W_GENERIC_KEYWORDS = 0.05;

    @Inject
    AppConfig config;

    @Inject
    SemanticSkillMatcher semanticSkillMatcher;

    List<SearchService.ScoredCandidate> scoreAndRank(
            List<SearchService.RawNodeCandidate> candidates,
            SearchQuery parsed,
            Map<UUID, Map<String, Short>> levelCacheByNode,
            SearchService.MatchContext context) {
        List<String> mustHaveSkills = parsed.mustHave() != null && parsed.mustHave().skills() != null
                ? parsed.mustHave().skills() : List.of();
        List<String> niceToHaveSkills = parsed.niceToHave() != null && parsed.niceToHave().skills() != null
                ? parsed.niceToHave().skills() : List.of();
        List<String> mustHaveLanguages = parsed.mustHave() != null && parsed.mustHave().languages() != null
                ? parsed.mustHave().languages() : List.of();
        List<String> mustHaveIndustries = parsed.mustHave() != null && parsed.mustHave().industries() != null
                ? parsed.mustHave().industries() : List.of();
        List<String> niceToHaveIndustries = parsed.niceToHave() != null && parsed.niceToHave().industries() != null
                ? parsed.niceToHave().industries() : List.of();
        List<String> keywords = parsed.keywords() != null ? parsed.keywords() : List.of();
        List<String> negativeSkills = parsed.negativeFilters() != null && parsed.negativeFilters().skills() != null
                ? parsed.negativeFilters().skills() : List.of();

        List<SkillWithLevel> mustHaveWithLevel = parsed.mustHave() != null
                ? parsed.mustHave().skillsWithLevel() : null;
        List<SkillWithLevel> niceToHaveWithLevel = parsed.niceToHave() != null
                ? parsed.niceToHave().skillsWithLevel() : null;
        Map<UUID, Map<String, Short>> safeLevelCacheByNode = levelCacheByNode == null
                ? Map.of()
                : levelCacheByNode;

        List<SearchService.ScoredCandidate> scored = new ArrayList<>();
        for (SearchService.RawNodeCandidate c : candidates) {
            if (c.nodeType() == NodeType.USER) {
                scored.add(scoreUserNode(c, mustHaveSkills, niceToHaveSkills,
                        mustHaveLanguages, mustHaveIndustries, niceToHaveIndustries,
                        mustHaveWithLevel, niceToHaveWithLevel, safeLevelCacheByNode.get(c.nodeId()), negativeSkills, context));
            } else {
                scored.add(scoreGenericNode(c, keywords, mustHaveSkills, niceToHaveSkills, negativeSkills, context));
            }
        }

        scored.sort(Comparator.comparingDouble(SearchService.ScoredCandidate::score).reversed());
        return rerank(scored, parsed);
    }

    private SearchService.ScoredCandidate scoreUserNode(SearchService.RawNodeCandidate c,
                                                        List<String> mustHaveSkills, List<String> niceToHaveSkills,
                                                        List<String> mustHaveLanguages,
                                                        List<String> mustHaveIndustries, List<String> niceToHaveIndustries,
                                                        List<SkillWithLevel> mustHaveWithLevel,
                                                        List<SkillWithLevel> niceToHaveWithLevel,
                                                        Map<String, Short> cachedLevelsBySkillName,
                                                        List<String> negativeSkills,
                                                        SearchService.MatchContext context) {
        List<String> candidateSkills = c.tags() != null ? new ArrayList<>(c.tags()) : new ArrayList<>();
        List<String> toolsAndTech = sdListOrEmpty(c.structuredData(), "tools_and_tech");
        List<String> softSkills = sdListOrEmpty(c.structuredData(), "skills_soft");
        candidateSkills.addAll(toolsAndTech);
        candidateSkills.addAll(softSkills);
        if (cachedLevelsBySkillName != null && !cachedLevelsBySkillName.isEmpty()) {
            candidateSkills.addAll(cachedLevelsBySkillName.keySet());
        }
        candidateSkills = SearchMatchingUtils.deduplicateTerms(candidateSkills);

        double skillMatchThreshold = config.search().skillMatchThreshold();
        List<String> matchedMust = semanticSkillMatcher.matchSkills(mustHaveSkills, candidateSkills, skillMatchThreshold).stream()
                .map(SemanticSkillMatcher.SemanticMatch::querySkill)
                .distinct()
                .toList();
        Set<String> matchedMustNormalized = new java.util.LinkedHashSet<>();
        for (String skill : matchedMust) {
            matchedMustNormalized.add(MatchingUtils.normalizeTerm(skill));
        }
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

        List<String> languagesSpoken = sdListOrEmpty(c.structuredData(), "languages_spoken");
        double languageScore = mustHaveLanguages.isEmpty() ? 1.0
                : MatchingUtils.intersectCaseInsensitive(mustHaveLanguages, languagesSpoken).isEmpty() ? 0.0 : 1.0;

        List<String> allIndustries = MatchingUtils.combineLists(mustHaveIndustries, niceToHaveIndustries);
        double industryScore = 0.0;
        if (!allIndustries.isEmpty()) {
            List<String> candidateIndustries = sdStringListOrEmpty(c.structuredData(), "industries");
            if (!candidateIndustries.isEmpty()) {
                List<String> matched = MatchingUtils.intersectCaseInsensitive(allIndustries, candidateIndustries);
                industryScore = (double) matched.size() / allIndustries.size();
            }
        }
        double geographyScore = GeographyUtils.geographyScore(
                context.referenceCountry(), c.country(), context.referenceWorkMode());
        String geographyReason = GeographyUtils.geographyReason(
                context.referenceCountry(), c.country(), context.referenceWorkMode());

        double rawScore = c.cosineSim() * W_EMBEDDING
                + mustHaveCoverage * W_MUST_HAVE
                + niceToHaveBonus * W_NICE_TO_HAVE
                + languageScore * W_LANGUAGE
                + industryScore * W_INDUSTRY
                + geographyScore * W_GEOGRAPHY;

        if (!missingMust.isEmpty() && !mustHaveSkills.isEmpty()) {
            double missingRatio = (double) missingMust.size() / mustHaveSkills.size();
            rawScore *= Math.max(0.0, 1.0 - missingRatio * 0.8);
        }
        if (!mustHaveSkills.isEmpty() && mustHaveCoverage == 0.0) {
            rawScore = 0.0;
        }

        List<String> matchedNegative = MatchingUtils.intersectCaseInsensitive(negativeSkills, candidateSkills);
        if (!matchedNegative.isEmpty() && !negativeSkills.isEmpty()) {
            double matchedNegativeRatio = (double) matchedNegative.size() / negativeSkills.size();
            rawScore *= Math.max(0.0, 1.0 - matchedNegativeRatio * 0.8);
        }

        List<String> reasonCodes = new ArrayList<>();
        if (c.cosineSim() >= 0.65) reasonCodes.add("SEMANTIC_SIMILARITY");
        if (!matchedMust.isEmpty()) reasonCodes.add("MUST_HAVE_SKILLS");
        if (!matchedNice.isEmpty()) reasonCodes.add("NICE_TO_HAVE_SKILLS");
        if (languageScore > 0) reasonCodes.add("LANGUAGE_MATCH");
        if (industryScore > 0) reasonCodes.add("INDUSTRY_MATCH");
        if (geographyScore > 0) reasonCodes.add("LOCATION_COMPATIBLE");

        SearchMatchBreakdown breakdown = new SearchMatchBreakdown(
                StringUtils.round3(c.cosineSim()),
                StringUtils.round3(mustHaveCoverage),
                StringUtils.round3(niceToHaveBonus),
                StringUtils.round3(languageScore),
                StringUtils.round3(industryScore),
                StringUtils.round3(geographyScore),
                StringUtils.round3(rawScore),
                matchedMust,
                matchedNice,
                missingMust,
                reasonCodes,
                geographyReason
        );

        return new SearchService.ScoredCandidate(c, breakdown, rawScore);
    }

    private SearchService.ScoredCandidate scoreGenericNode(SearchService.RawNodeCandidate c,
                                                           List<String> keywords,
                                                           List<String> mustHaveSkills,
                                                           List<String> niceToHaveSkills,
                                                           List<String> negativeSkills,
                                                           SearchService.MatchContext context) {
        List<String> nodeTags = c.tags() != null ? c.tags() : List.of();
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
        if (c.title() != null) nodeText.add(c.title());
        if (c.description() != null) nodeText.add(c.description());

        List<String> matchedKeywordTerms = MatchingUtils.intersectCaseInsensitive(keywords, nodeText);
        List<String> matchedMustText = MatchingUtils.intersectCaseInsensitive(mustHaveSkills, nodeText);
        List<String> matchedMust = SearchMatchingUtils.deduplicateTerms(
                MatchingUtils.combineLists(matchedMustSemantic, matchedMustText));
        Set<String> matchedMustNormalized = new java.util.LinkedHashSet<>();
        for (String skill : matchedMust) {
            matchedMustNormalized.add(MatchingUtils.normalizeTerm(skill));
        }
        List<String> missingMust = mustHaveSkills.stream()
                .filter(s -> !matchedMustNormalized.contains(MatchingUtils.normalizeTerm(s)))
                .toList();

        double mustHaveCoverage = mustHaveSkills.isEmpty() ? 1.0
                : (double) matchedMust.size() / mustHaveSkills.size();
        double niceToHaveBonus = niceToHaveSkills.isEmpty() ? 0.0
                : (double) matchedNice.size() / niceToHaveSkills.size();
        double keywordScore = keywords.isEmpty() ? 0.0
                : (double) matchedKeywordTerms.size() / keywords.size();
        double geographyScore = GeographyUtils.geographyScore(
                context.referenceCountry(), c.country(), context.referenceWorkMode());
        String geographyReason = GeographyUtils.geographyReason(
                context.referenceCountry(), c.country(), context.referenceWorkMode());

        double rawScore = c.cosineSim() * W_GENERIC_EMBEDDING
                + mustHaveCoverage * W_GENERIC_MUST_HAVE
                + niceToHaveBonus * W_GENERIC_NICE_TO_HAVE
                + keywordScore * W_GENERIC_KEYWORDS
                + geographyScore * W_GEOGRAPHY;

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
        if (c.cosineSim() >= 0.60) reasonCodes.add("SEMANTIC_SIMILARITY");
        if (!matchedMust.isEmpty()) reasonCodes.add("MUST_HAVE_SKILLS");
        if (!matchedNice.isEmpty()) reasonCodes.add("NICE_TO_HAVE_SKILLS");
        if (!matchedKeywordTerms.isEmpty()) reasonCodes.add("KEYWORD_MATCH");
        if (geographyScore > 0) reasonCodes.add("LOCATION_COMPATIBLE");
        reasonCodes.add("NODE_" + c.nodeType().name());

        SearchMatchBreakdown breakdown = new SearchMatchBreakdown(
                StringUtils.round3(c.cosineSim()),
                StringUtils.round3(mustHaveCoverage),
                StringUtils.round3(niceToHaveBonus),
                0,
                0,
                StringUtils.round3(geographyScore),
                StringUtils.round3(rawScore),
                matchedMust,
                matchedNice,
                missingMust,
                reasonCodes,
                geographyReason
        );

        return new SearchService.ScoredCandidate(c, breakdown, rawScore);
    }

    private List<SearchService.ScoredCandidate> rerank(List<SearchService.ScoredCandidate> candidates, SearchQuery parsed) {
        String targetSeniority = parsed.seniority();
        Seniority target = (targetSeniority == null || "unknown".equalsIgnoreCase(targetSeniority))
                ? null
                : SqlParsingUtils.parseEnum(Seniority.class, targetSeniority.toUpperCase());
        if (target == null) {
            return candidates;
        }

        List<SearchService.ScoredCandidate> reranked = new ArrayList<>(candidates);
        for (int i = 0; i < reranked.size(); i++) {
            SearchService.ScoredCandidate sc = reranked.get(i);
            if (sc.node().nodeType() != NodeType.USER) continue;
            double adjusted = sc.score();

            String seniorityStr = sdString(sc.node().structuredData(), "seniority");
            Seniority candidateSeniority = SqlParsingUtils.parseEnum(Seniority.class, seniorityStr);
            if (candidateSeniority == target) {
                adjusted *= 1.05;
            } else if (candidateSeniority != null) {
                adjusted *= 0.95;
            }

            reranked.set(i, new SearchService.ScoredCandidate(sc.node(), sc.breakdown(), adjusted));
        }

        reranked.sort(Comparator.comparingDouble(SearchService.ScoredCandidate::score).reversed());
        return reranked;
    }
}
