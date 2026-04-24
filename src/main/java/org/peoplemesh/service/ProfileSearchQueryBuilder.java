package org.peoplemesh.service;

import static org.peoplemesh.util.StructuredDataUtils.sdListOrEmpty;
import static org.peoplemesh.util.StructuredDataUtils.sdStringListOrEmpty;
import static org.peoplemesh.util.StructuredDataUtils.sdString;

import jakarta.enterprise.context.ApplicationScoped;
import org.peoplemesh.domain.dto.SearchOptions;
import org.peoplemesh.domain.dto.SearchQuery;
import org.peoplemesh.domain.dto.ProfileSchema;
import org.peoplemesh.domain.model.MeshNode;
import org.peoplemesh.util.MatchingUtils;
import org.peoplemesh.util.ProfileSchemaNormalization;
import org.peoplemesh.util.SearchMatchingUtils;
import org.peoplemesh.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ApplicationScoped
public class ProfileSearchQueryBuilder {
    private static final int MAX_PROFILE_NICE_SKILLS = 50;
    private static final boolean INCLUDE_NICE_TO_HAVE = true;
    private static final boolean INCLUDE_INTERESTS_IN_EMBEDDING_TEXT = true;

    public SearchQuery buildFromUserNode(MeshNode node) {
        return buildFromUserNode(node, null);
    }

    public SearchQuery buildFromUserNode(MeshNode node, SearchOptions tuning) {
        if (node == null) {
            return emptyQuery();
        }
        List<String> profileSkills = SearchMatchingUtils.deduplicateTerms(MatchingUtils.combineLists(
                MatchingUtils.combineLists(node.tags, sdListOrEmpty(node.structuredData, "tools_and_tech")),
                sdListOrEmpty(node.structuredData, "skills_soft")));
        List<String> roles = SearchMatchingUtils.deduplicateTerms(StringUtils.splitCommaSeparated(node.description));
        List<String> languages = SearchMatchingUtils.deduplicateTerms(ProfileSchemaNormalization.normalizeLanguages(
                sdListOrEmpty(node.structuredData, "languages_spoken"),
                ProfileSchema.MAX_LANGUAGES_SPOKEN,
                ProfileSchema.MAX_LANGUAGE_SPOKEN_LENGTH
        ));
        List<String> industries = SearchMatchingUtils.deduplicateTerms(readIndustries(node));
        // Keep geography as a ranking signal via MatchContext, not as a hard SQL filter.
        // Setting must_have.location here would force country-filtering for /matches/me.
        List<String> location = Collections.emptyList();

        List<String> profileInterests = SearchMatchingUtils.deduplicateTerms(MatchingUtils.combineLists(
                MatchingUtils.combineLists(sdListOrEmpty(node.structuredData, "learning_areas"),
                        sdListOrEmpty(node.structuredData, "project_types")),
                MatchingUtils.combineLists(sdListOrEmpty(node.structuredData, "hobbies"),
                        MatchingUtils.combineLists(sdListOrEmpty(node.structuredData, "sports"),
                                sdListOrEmpty(node.structuredData, "causes")))));

        List<String> niceSkills = capList(SearchMatchingUtils.deduplicateTerms(MatchingUtils.combineLists(
                profileSkills,
                profileInterests)), profileNiceSkillsCap(tuning));
        List<String> effectiveNiceSkills = profileIncludeNiceToHave(tuning) ? niceSkills : Collections.emptyList();
        List<String> keywords = SearchMatchingUtils.deduplicateTerms(MatchingUtils.combineLists(
                MatchingUtils.combineLists(profileSkills, roles),
                effectiveNiceSkills));

        String seniority = normalizeSeniority(sdString(node.structuredData, "seniority"));
        String embeddingText = buildEmbeddingText(roles, profileSkills, languages, industries, effectiveNiceSkills, tuning);

        // Keep languages as a soft ranking signal, not a hard candidate pre-filter.
        // Hard language filtering in SearchService can zero-out MyMesh for profiles with rare language labels.
        List<String> mustHaveLanguages = Collections.emptyList();
        SearchQuery.MustHaveFilters mustHave = new SearchQuery.MustHaveFilters(
                Collections.emptyList(),
                null,
                roles,
                mustHaveLanguages,
                location,
                industries
        );
        SearchQuery.NiceToHaveFilters niceToHave = new SearchQuery.NiceToHaveFilters(
                effectiveNiceSkills,
                null,
                Collections.emptyList(),
                Collections.emptyList()
        );
        SearchQuery.NegativeFilters negativeFilters = new SearchQuery.NegativeFilters(
                null,
                Collections.emptyList(),
                Collections.emptyList()
        );
        return new SearchQuery(
                mustHave,
                niceToHave,
                seniority,
                negativeFilters,
                keywords,
                embeddingText,
                "all"
        );
    }

    private SearchQuery emptyQuery() {
        return new SearchQuery(
                new SearchQuery.MustHaveFilters(Collections.emptyList(), null, Collections.emptyList(),
                        Collections.emptyList(), Collections.emptyList(), Collections.emptyList()),
                new SearchQuery.NiceToHaveFilters(Collections.emptyList(), null, Collections.emptyList(), Collections.emptyList()),
                "unknown",
                new SearchQuery.NegativeFilters(null, Collections.emptyList(), Collections.emptyList()),
                Collections.emptyList(),
                "search",
                "all"
        );
    }

    private String buildEmbeddingText(
            List<String> roles,
            List<String> profileSkills,
            List<String> languages,
            List<String> industries,
            List<String> niceSkills,
            SearchOptions tuning) {
        List<String> parts = new ArrayList<>();
        if (!roles.isEmpty()) {
            parts.add("roles: " + String.join(", ", capList(roles, 8)));
        }
        if (!profileSkills.isEmpty()) {
            parts.add("skills: " + String.join(", ", capList(profileSkills, 20)));
        }
        if (!languages.isEmpty()) {
            parts.add("languages: " + String.join(", ", capList(languages, 6)));
        }
        if (!industries.isEmpty()) {
            parts.add("industries: " + String.join(", ", capList(industries, 8)));
        }
        if (profileIncludeInterestsInEmbeddingText(tuning) && !niceSkills.isEmpty()) {
            parts.add("focus: " + String.join(", ", capList(niceSkills, 12)));
        }
        if (parts.isEmpty()) {
            return "search";
        }
        return String.join(". ", parts);
    }

    private List<String> readIndustries(MeshNode node) {
        return node == null ? Collections.emptyList() : sdStringListOrEmpty(node.structuredData, "industries");
    }

    private String normalizeSeniority(String seniority) {
        if (seniority == null || seniority.isBlank()) {
            return "unknown";
        }
        return seniority.trim().toLowerCase();
    }

    private List<String> capList(List<String> values, int maxSize) {
        if (values == null || values.isEmpty() || maxSize <= 0 || values.size() <= maxSize) {
            return values != null ? values : Collections.emptyList();
        }
        return values.subList(0, maxSize);
    }

    private int profileNiceSkillsCap(SearchOptions tuning) {
        if (tuning != null && tuning.profileNiceSkillsCap() != null) {
            return Math.max(0, tuning.profileNiceSkillsCap());
        }
        return MAX_PROFILE_NICE_SKILLS;
    }

    private boolean profileIncludeNiceToHave(SearchOptions tuning) {
        if (tuning != null && tuning.profileIncludeNiceToHave() != null) {
            return tuning.profileIncludeNiceToHave();
        }
        return INCLUDE_NICE_TO_HAVE;
    }

    private boolean profileIncludeInterestsInEmbeddingText(SearchOptions tuning) {
        if (tuning != null && tuning.profileIncludeInterestsInEmbeddingText() != null) {
            return tuning.profileIncludeInterestsInEmbeddingText();
        }
        return INCLUDE_INTERESTS_IN_EMBEDDING_TEXT;
    }
}
