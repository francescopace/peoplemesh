package org.peoplemesh.service;

import static org.peoplemesh.util.StructuredDataUtils.sdListOrEmpty;
import static org.peoplemesh.util.StructuredDataUtils.sdStringListOrEmpty;
import static org.peoplemesh.util.StructuredDataUtils.sdString;

import jakarta.enterprise.context.ApplicationScoped;
import org.peoplemesh.domain.dto.SearchQuery;
import org.peoplemesh.domain.model.MeshNode;
import org.peoplemesh.util.MatchingUtils;
import org.peoplemesh.util.SearchMatchingUtils;
import org.peoplemesh.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ApplicationScoped
public class ProfileSearchQueryBuilder {
    private static final int MAX_PROFILE_NICE_SKILLS = 20;

    public SearchQuery buildFromUserNode(MeshNode node) {
        if (node == null) {
            return emptyQuery();
        }
        List<String> profileSkills = SearchMatchingUtils.deduplicateTerms(MatchingUtils.combineLists(
                MatchingUtils.combineLists(node.tags, sdListOrEmpty(node.structuredData, "tools_and_tech")),
                sdListOrEmpty(node.structuredData, "skills_soft")));
        List<String> roles = SearchMatchingUtils.deduplicateTerms(StringUtils.splitCommaSeparated(node.description));
        List<String> languages = SearchMatchingUtils.deduplicateTerms(sdListOrEmpty(node.structuredData, "languages_spoken"));
        List<String> industries = SearchMatchingUtils.deduplicateTerms(readIndustries(node));
        // Keep geography as a ranking signal via MatchContext, not as a hard SQL filter.
        // Setting must_have.location here would force country-filtering for /matches/me.
        List<String> location = Collections.emptyList();

        List<String> niceSkills = capList(SearchMatchingUtils.deduplicateTerms(MatchingUtils.combineLists(
                profileSkills,
                MatchingUtils.combineLists(
                        sdListOrEmpty(node.structuredData, "topics_frequent"),
                        sdListOrEmpty(node.structuredData, "learning_areas")))), MAX_PROFILE_NICE_SKILLS);
        List<String> keywords = SearchMatchingUtils.deduplicateTerms(MatchingUtils.combineLists(
                MatchingUtils.combineLists(profileSkills, roles),
                niceSkills));

        String seniority = normalizeSeniority(sdString(node.structuredData, "seniority"));
        String embeddingText = buildEmbeddingText(node, roles, profileSkills, industries, niceSkills);

        SearchQuery.MustHaveFilters mustHave = new SearchQuery.MustHaveFilters(
                Collections.emptyList(),
                null,
                roles,
                languages,
                location,
                industries
        );
        SearchQuery.NiceToHaveFilters niceToHave = new SearchQuery.NiceToHaveFilters(
                niceSkills,
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
            MeshNode node,
            List<String> roles,
            List<String> profileSkills,
            List<String> industries,
            List<String> niceSkills) {
        List<String> parts = new ArrayList<>();
        if (node.title != null && !node.title.isBlank()) {
            parts.add(node.title.trim());
        }
        if (node.description != null && !node.description.isBlank()) {
            parts.add(node.description.trim());
        }
        if (!roles.isEmpty()) {
            parts.add(String.join(" ", roles));
        }
        if (!profileSkills.isEmpty()) {
            parts.add(String.join(" ", profileSkills));
        }
        if (!industries.isEmpty()) {
            parts.add(String.join(" ", industries));
        }
        if (!niceSkills.isEmpty()) {
            parts.add(String.join(" ", niceSkills));
        }
        if (parts.isEmpty()) {
            return "search";
        }
        return String.join(" ", parts);
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
}
