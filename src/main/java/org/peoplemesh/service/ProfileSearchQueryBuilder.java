package org.peoplemesh.service;

import static org.peoplemesh.util.StructuredDataUtils.sdListOrEmpty;
import static org.peoplemesh.util.StructuredDataUtils.sdString;

import jakarta.enterprise.context.ApplicationScoped;
import org.peoplemesh.domain.dto.SearchQuery;
import org.peoplemesh.domain.model.MeshNode;
import org.peoplemesh.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class ProfileSearchQueryBuilder {
    private static final int MAX_PROFILE_NICE_SKILLS = 20;

    public SearchQuery buildFromUserNode(MeshNode node) {
        if (node == null) {
            return emptyQuery();
        }
        List<String> profileSkills = dedupe(MatchingUtils.combineLists(
                MatchingUtils.combineLists(node.tags, sdListOrEmpty(node.structuredData, "tools_and_tech")),
                sdListOrEmpty(node.structuredData, "skills_soft")));
        List<String> roles = dedupe(StringUtils.splitCommaSeparated(node.description));
        List<String> languages = dedupe(sdListOrEmpty(node.structuredData, "languages_spoken"));
        List<String> industries = dedupe(readIndustries(node));
        // Keep geography as a ranking signal via MatchContext, not as a hard SQL filter.
        // Setting must_have.location here would force country-filtering for /matches/me.
        List<String> location = Collections.emptyList();

        List<String> niceSkills = capList(dedupe(MatchingUtils.combineLists(
                profileSkills,
                MatchingUtils.combineLists(
                        sdListOrEmpty(node.structuredData, "topics_frequent"),
                        sdListOrEmpty(node.structuredData, "learning_areas")))), MAX_PROFILE_NICE_SKILLS);
        List<String> keywords = dedupe(MatchingUtils.combineLists(
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
        if (node == null || node.structuredData == null) {
            return Collections.emptyList();
        }
        Object rawIndustries = node.structuredData.get("industries");
        if (rawIndustries instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        if (rawIndustries instanceof String value && !value.isBlank()) {
            String[] split = value.split(",");
            List<String> industries = new ArrayList<>(split.length);
            for (String token : split) {
                String trimmed = token != null ? token.trim() : "";
                if (!trimmed.isEmpty()) {
                    industries.add(trimmed);
                }
            }
            return industries;
        }
        return Collections.emptyList();
    }

    private String normalizeSeniority(String seniority) {
        if (seniority == null || seniority.isBlank()) {
            return "unknown";
        }
        return seniority.trim().toLowerCase();
    }

    private List<String> dedupe(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> seen = new LinkedHashSet<>();
        List<String> out = new ArrayList<>();
        for (String value : raw) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String normalized = MatchingUtils.normalizeTerm(value);
            if (seen.add(normalized)) {
                out.add(value.trim());
            }
        }
        return out;
    }

    private List<String> capList(List<String> values, int maxSize) {
        if (values == null || values.isEmpty() || maxSize <= 0 || values.size() <= maxSize) {
            return values != null ? values : Collections.emptyList();
        }
        return values.subList(0, maxSize);
    }
}
