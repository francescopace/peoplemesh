package org.peoplemesh.service;

import static org.peoplemesh.util.StructuredDataUtils.sdListOrEmpty;
import static org.peoplemesh.util.StructuredDataUtils.sdString;
import static org.peoplemesh.util.StructuredDataUtils.sdStringListOrEmpty;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.domain.dto.ProfileSchema;
import org.peoplemesh.domain.dto.SearchMatchBreakdown;
import org.peoplemesh.domain.dto.SearchOptions;
import org.peoplemesh.domain.dto.SearchQuery;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.enums.Seniority;
import org.peoplemesh.util.GeographyUtils;
import org.peoplemesh.util.MatchingUtils;
import org.peoplemesh.util.ProfileSchemaNormalization;
import org.peoplemesh.util.SearchMatchingUtils;
import org.peoplemesh.util.SqlParsingUtils;
import org.peoplemesh.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class SearchScoringEngine {

    // All Node weights
    private static final double W_EMBEDDING = 0.55;
    private static final double W_MUST_HAVE = 0.50;
    private static final double W_NICE_TO_HAVE = 0.40;
    private static final double W_GEOGRAPHY = 0.25;

    // USER node weights
    private static final double W_LANGUAGE = 0.10;
    private static final double W_INDUSTRY = 0.10;
    private static final double W_SENIORITY = 0.01;

    // Non-user node weights.
    private static final double W_GENERIC_KEYWORDS = 0.20;

    @Inject
    AppConfig config;

    @Inject
    SemanticSkillMatcher semanticSkillMatcher;

    List<ScoredCandidate> scoreAndRank(
            List<RawNodeCandidate> candidates,
            SearchQuery parsed,
            SearchService.MatchContext context) {
        return scoreAndRank(candidates, parsed, context, null);
    }

    List<ScoredCandidate> scoreAndRank(
            List<RawNodeCandidate> candidates,
            SearchQuery parsed,
            SearchService.MatchContext context,
            SearchOptions tuning) {
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

        double skillMatchThreshold = resolveSkillMatchThreshold(tuning);
        Weights userWeights = resolveUserWeights(tuning);
        GenericWeights genericWeights = resolveGenericWeights(tuning);
        Seniority targetSeniority = parseTargetSeniority(parsed.seniority());
        Map<String, List<String>> mustHaveSkillNeighbors = semanticSkillMatcher.resolveSkillNeighbors(
                mustHaveSkills,
                skillMatchThreshold);
        Map<String, List<String>> niceToHaveSkillNeighbors = semanticSkillMatcher.resolveSkillNeighbors(
                niceToHaveSkills,
                skillMatchThreshold);

        List<ScoredCandidate> scored = new ArrayList<>();
        // Geography always participates in ranking; when location context is missing
        // GeographyUtils returns neutral/unknown (0), but the weight is consistently applied.
        boolean geographyRequested = true;
        for (RawNodeCandidate c : candidates) {
            if (c.nodeType() == NodeType.USER) {
                scored.add(scoreUserNode(c, mustHaveSkills, niceToHaveSkills,
                        mustHaveLanguages, mustHaveIndustries, niceToHaveIndustries,
                        negativeSkills, context, geographyRequested, userWeights, mustHaveSkillNeighbors,
                        niceToHaveSkillNeighbors, targetSeniority));
            } else {
                scored.add(scoreGenericNode(c, keywords, mustHaveSkills, niceToHaveSkills,
                        negativeSkills, context, geographyRequested, mustHaveSkillNeighbors,
                        niceToHaveSkillNeighbors, genericWeights));
            }
        }

        scored.sort(Comparator.comparingDouble(ScoredCandidate::score).reversed());
        return scored;
    }

    private ScoredCandidate scoreUserNode(RawNodeCandidate c,
                                                        List<String> mustHaveSkills, List<String> niceToHaveSkills,
                                                        List<String> mustHaveLanguages,
                                                        List<String> mustHaveIndustries, List<String> niceToHaveIndustries,
                                                        List<String> negativeSkills,
                                                        SearchService.MatchContext context,
                                                        boolean geographyRequested,
                                                        Weights userWeights,
                                                        Map<String, List<String>> mustHaveSkillNeighbors,
                                                        Map<String, List<String>> niceToHaveSkillNeighbors,
                                                        Seniority targetSeniority) {
        List<String> candidateSkills = c.tags() != null ? new ArrayList<>(c.tags()) : new ArrayList<>();
        List<String> toolsAndTech = sdListOrEmpty(c.structuredData(), "tools_and_tech");
        List<String> softSkills = sdListOrEmpty(c.structuredData(), "skills_soft");
        candidateSkills.addAll(toolsAndTech);
        candidateSkills.addAll(softSkills);
        candidateSkills = SearchMatchingUtils.deduplicateTerms(candidateSkills);

        List<String> matchedMust = semanticSkillMatcher.matchSkillsWithResolvedOptions(
                        mustHaveSkills,
                        candidateSkills,
                        mustHaveSkillNeighbors).stream()
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
        double mustHaveCoverage = mustHaveSkills.isEmpty() ? 0.0
                : (double) matchedMust.size() / mustHaveSkills.size();

        List<String> matchedNice = semanticSkillMatcher.matchSkillsWithResolvedOptions(
                        niceToHaveSkills,
                        candidateSkills,
                        niceToHaveSkillNeighbors).stream()
                .map(SemanticSkillMatcher.SemanticMatch::querySkill)
                .distinct()
                .toList();
        double niceToHaveBonus = niceToHaveSkills.isEmpty() ? 0.0
                : (double) matchedNice.size() / niceToHaveSkills.size();

        List<String> languagesSpoken = ProfileSchemaNormalization.normalizeLanguages(
                sdListOrEmpty(c.structuredData(), "languages_spoken"),
                ProfileSchema.MAX_LANGUAGES_SPOKEN,
                ProfileSchema.MAX_LANGUAGE_SPOKEN_LENGTH
        );
        List<String> normalizedMustHaveLanguages = ProfileSchemaNormalization.normalizeLanguages(
                mustHaveLanguages,
                ProfileSchema.MAX_LANGUAGES_SPOKEN,
                ProfileSchema.MAX_LANGUAGE_SPOKEN_LENGTH
        );
        double languageScore = normalizedMustHaveLanguages == null || normalizedMustHaveLanguages.isEmpty() ? 0.0
                : MatchingUtils.intersectCaseInsensitive(normalizedMustHaveLanguages, languagesSpoken).isEmpty() ? 0.0 : 1.0;

        List<String> allIndustries = MatchingUtils.combineLists(mustHaveIndustries, niceToHaveIndustries);
        double industryScore = 0.0;
        if (!allIndustries.isEmpty()) {
            List<String> candidateIndustries = sdStringListOrEmpty(c.structuredData(), "industries");
            if (!candidateIndustries.isEmpty()) {
                List<String> matched = MatchingUtils.intersectCaseInsensitive(allIndustries, candidateIndustries);
                industryScore = (double) matched.size() / allIndustries.size();
            }
        }
        double geographyScore = geographyRequested
                ? GeographyUtils.geographyScore(context.referenceCountry(), c.country(), context.referenceWorkMode())
                : 0.0;
        String geographyReason = geographyRequested
                ? GeographyUtils.geographyReason(context.referenceCountry(), c.country(), context.referenceWorkMode())
                : "location_not_requested";
        String seniorityRaw = sdString(c.structuredData(), "seniority");
        Seniority candidateSeniority = SqlParsingUtils.parseEnum(Seniority.class, seniorityRaw);
        double seniorityScore = resolveSeniorityScore(targetSeniority, candidateSeniority);

        double rawScore = c.cosineSim() * userWeights.embedding()
                + mustHaveCoverage * userWeights.mustHave()
                + niceToHaveBonus * userWeights.niceToHave()
                + languageScore * userWeights.language()
                + industryScore * userWeights.industry()
                + geographyScore * userWeights.geography()
                + seniorityScore * userWeights.seniority();
        double mustHavePenaltyFactor = 1.0;

        if (!missingMust.isEmpty() && !mustHaveSkills.isEmpty()) {
            double missingRatio = (double) missingMust.size() / mustHaveSkills.size();
            mustHavePenaltyFactor = Math.max(0.0, 1.0 - missingRatio * 0.8);
            rawScore *= mustHavePenaltyFactor;
        }
        if (!mustHaveSkills.isEmpty() && mustHaveCoverage == 0.0) {
            rawScore = 0.0;
        }

        List<String> matchedNegative = MatchingUtils.intersectCaseInsensitive(negativeSkills, candidateSkills);
        double negativePenaltyFactor = 1.0;
        if (!matchedNegative.isEmpty() && !negativeSkills.isEmpty()) {
            double matchedNegativeRatio = (double) matchedNegative.size() / negativeSkills.size();
            negativePenaltyFactor = Math.max(0.0, 1.0 - matchedNegativeRatio * 0.8);
            rawScore *= negativePenaltyFactor;
        }

        List<String> reasonCodes = new ArrayList<>();
        if (c.cosineSim() >= 0.65) reasonCodes.add("SEMANTIC_SIMILARITY");
        if (!matchedMust.isEmpty()) reasonCodes.add("MUST_HAVE_SKILLS");
        if (!matchedNice.isEmpty()) reasonCodes.add("NICE_TO_HAVE_SKILLS");
        if (languageScore > 0) reasonCodes.add("LANGUAGE_MATCH");
        if (industryScore > 0) reasonCodes.add("INDUSTRY_MATCH");
        if (geographyScore > 0) reasonCodes.add("LOCATION_COMPATIBLE");
        if (targetSeniority != null && candidateSeniority == targetSeniority) reasonCodes.add("SENIORITY_MATCH");
        if (targetSeniority != null && candidateSeniority != null && candidateSeniority != targetSeniority) {
            reasonCodes.add("SENIORITY_MISMATCH");
        }

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
                geographyReason,
                !mustHaveSkills.isEmpty(),
                !niceToHaveSkills.isEmpty(),
                !mustHaveLanguages.isEmpty(),
                !allIndustries.isEmpty(),
                geographyRequested,
                StringUtils.round3(mustHavePenaltyFactor),
                StringUtils.round3(negativePenaltyFactor),
                StringUtils.round3(seniorityScore),
                0.0,
                userWeights.embedding(),
                userWeights.mustHave(),
                userWeights.niceToHave(),
                userWeights.language(),
                userWeights.industry(),
                userWeights.geography(),
                userWeights.seniority(),
                0.0
        );

        return new ScoredCandidate(c, breakdown, rawScore);
    }

    private ScoredCandidate scoreGenericNode(RawNodeCandidate c,
                                                           List<String> keywords,
                                                           List<String> mustHaveSkills,
                                                           List<String> niceToHaveSkills,
                                                           List<String> negativeSkills,
                                                           SearchService.MatchContext context,
                                                           boolean geographyRequested,
                                                           Map<String, List<String>> mustHaveSkillNeighbors,
                                                           Map<String, List<String>> niceToHaveSkillNeighbors,
                                                           GenericWeights genericWeights) {
        List<String> nodeTags = c.tags() != null ? c.tags() : List.of();
        List<String> matchedMustSemantic = semanticSkillMatcher.matchSkillsWithResolvedOptions(
                        mustHaveSkills,
                        nodeTags,
                        mustHaveSkillNeighbors).stream()
                .map(SemanticSkillMatcher.SemanticMatch::querySkill)
                .distinct()
                .toList();
        List<String> matchedNice = semanticSkillMatcher.matchSkillsWithResolvedOptions(
                        niceToHaveSkills,
                        nodeTags,
                        niceToHaveSkillNeighbors).stream()
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

        double mustHaveCoverage = mustHaveSkills.isEmpty() ? 0.0
                : (double) matchedMust.size() / mustHaveSkills.size();
        double niceToHaveBonus = niceToHaveSkills.isEmpty() ? 0.0
                : (double) matchedNice.size() / niceToHaveSkills.size();
        double keywordScore = keywords.isEmpty() ? 0.0
                : (double) matchedKeywordTerms.size() / keywords.size();
        double geographyScore = geographyRequested
                ? GeographyUtils.geographyScore(context.referenceCountry(), c.country(), context.referenceWorkMode())
                : 0.0;
        String geographyReason = geographyRequested
                ? GeographyUtils.geographyReason(context.referenceCountry(), c.country(), context.referenceWorkMode())
                : "location_not_requested";

        double rawScore = c.cosineSim() * genericWeights.embedding()
                + mustHaveCoverage * genericWeights.mustHave()
                + niceToHaveBonus * genericWeights.niceToHave()
                + keywordScore * genericWeights.keyword()
                + geographyScore * genericWeights.geography();
        double mustHavePenaltyFactor = 1.0;

        if (!mustHaveSkills.isEmpty() && !missingMust.isEmpty()) {
            double missingRatio = (double) missingMust.size() / mustHaveSkills.size();
            mustHavePenaltyFactor = Math.max(0.0, 1.0 - missingRatio * 0.8);
            rawScore *= mustHavePenaltyFactor;
        }
        if (!mustHaveSkills.isEmpty() && mustHaveCoverage == 0.0) {
            rawScore = 0.0;
        }
        double negativePenaltyFactor = 1.0;
        if (!negativeSkills.isEmpty()) {
            List<String> negativeMatches = MatchingUtils.intersectCaseInsensitive(negativeSkills, nodeText);
            if (!negativeMatches.isEmpty()) {
                double negativeRatio = (double) negativeMatches.size() / negativeSkills.size();
                negativePenaltyFactor = Math.max(0.0, 1.0 - negativeRatio * 0.8);
                rawScore *= negativePenaltyFactor;
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
                geographyReason,
                !mustHaveSkills.isEmpty(),
                !niceToHaveSkills.isEmpty(),
                false,
                false,
                geographyRequested,
                StringUtils.round3(mustHavePenaltyFactor),
                StringUtils.round3(negativePenaltyFactor),
                0.0,
                StringUtils.round3(keywordScore),
                genericWeights.embedding(),
                genericWeights.mustHave(),
                genericWeights.niceToHave(),
                0.0,
                0.0,
                genericWeights.geography(),
                0.0,
                genericWeights.keyword()
        );

        return new ScoredCandidate(c, breakdown, rawScore);
    }

    private record Weights(
            double embedding,
            double mustHave,
            double niceToHave,
            double language,
            double geography,
            double industry,
            double seniority
    ) {
    }

    private record GenericWeights(
            double embedding,
            double mustHave,
            double niceToHave,
            double geography,
            double keyword
    ) {
    }

    private Weights resolveUserWeights(SearchOptions tuning) {
        double embedding = tuning != null && tuning.weightEmbedding() != null
                ? tuning.weightEmbedding()
                : W_EMBEDDING;
        double mustHave = tuning != null && tuning.weightMustHave() != null
                ? tuning.weightMustHave()
                : W_MUST_HAVE;
        double niceToHave = tuning != null && tuning.weightNiceToHave() != null
                ? tuning.weightNiceToHave()
                : W_NICE_TO_HAVE;
        double language = tuning != null && tuning.weightLanguage() != null
                ? tuning.weightLanguage()
                : W_LANGUAGE;
        double geography = tuning != null && tuning.weightGeography() != null
                ? tuning.weightGeography()
                : W_GEOGRAPHY;
        double industry = tuning != null && tuning.weightIndustry() != null
                ? tuning.weightIndustry()
                : W_INDUSTRY;
        double seniority = tuning != null && tuning.weightSeniority() != null
                ? tuning.weightSeniority()
                : W_SENIORITY;
        return new Weights(embedding, mustHave, niceToHave, language, geography, industry, seniority);
    }

    private GenericWeights resolveGenericWeights(SearchOptions tuning) {
        double embedding = tuning != null && tuning.weightEmbedding() != null
                ? tuning.weightEmbedding()
                : W_EMBEDDING;
        double mustHave = tuning != null && tuning.weightMustHave() != null
                ? tuning.weightMustHave()
                : W_MUST_HAVE;
        double niceToHave = tuning != null && tuning.weightNiceToHave() != null
                ? tuning.weightNiceToHave()
                : W_NICE_TO_HAVE;
        double geography = tuning != null && tuning.weightGeography() != null
                ? tuning.weightGeography()
                : W_GEOGRAPHY;
        double keyword = tuning != null && tuning.weightGenericKeyword() != null
                ? tuning.weightGenericKeyword()
                : W_GENERIC_KEYWORDS;
        return new GenericWeights(embedding, mustHave, niceToHave, geography, keyword);
    }

    private double resolveSkillMatchThreshold(SearchOptions tuning) {
        return tuning != null && tuning.skillMatchThreshold() != null
                ? tuning.skillMatchThreshold()
                : config.skills().matchThreshold();
    }

    private Seniority parseTargetSeniority(String targetSeniority) {
        if (targetSeniority == null || "unknown".equalsIgnoreCase(targetSeniority)) {
            return null;
        }
        return SqlParsingUtils.parseEnum(Seniority.class, targetSeniority.toUpperCase());
    }

    private double resolveSeniorityScore(Seniority target, Seniority candidate) {
        if (target == null || candidate == null) {
            return 0.0;
        }
        return candidate == target ? 1.0 : 0.0;
    }
}
