package org.peoplemesh.domain.dto;

import org.peoplemesh.domain.enums.EmploymentType;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.enums.Seniority;
import org.peoplemesh.domain.enums.WorkMode;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SearchResultItem(
        UUID id,
        String resultType,
        double score,

        // Profile fields (null for node results).
        String displayName,
        String avatarUrl,
        List<String> roles,
        Seniority seniority,
        List<String> skillsTechnical,
        List<String> toolsAndTech,
        List<String> languagesSpoken,
        String country,
        String city,
        WorkMode workMode,
        EmploymentType employmentType,
        String slackHandle,
        String email,
        String telegramHandle,
        String mobilePhone,

        // Node fields (null for profile results).
        NodeType nodeType,
        String title,
        String description,
        List<String> tags,

        SearchMatchBreakdown breakdown,

        @JsonProperty("skill_levels")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Map<String, Integer> skillLevels
) {
    public static SearchResultItem profile(
            UUID profileId, double score, String displayName, String avatarUrl,
            List<String> roles, Seniority seniority,
            List<String> skillsTechnical, List<String> toolsAndTech,
            List<String> languagesSpoken, String country, String city,
            WorkMode workMode, EmploymentType employmentType,
            String slackHandle, String email, String telegramHandle, String mobilePhone,
            SearchMatchBreakdown breakdown,
            Map<String, Integer> skillLevels) {
        return new SearchResultItem(profileId, "profile", score,
                displayName, avatarUrl, roles, seniority, skillsTechnical, toolsAndTech,
                languagesSpoken, country, city, workMode, employmentType,
                slackHandle, email, telegramHandle, mobilePhone,
                null, null, null, null,
                breakdown, skillLevels);
    }

    public static SearchResultItem node(
            UUID nodeId, double score, NodeType nodeType, String title,
            String description, List<String> tags, String country,
            SearchMatchBreakdown breakdown) {
        return new SearchResultItem(nodeId, "node", score,
                null, null, null, null, null, null, null, country, null,
                null, null, null, null, null, null,
                nodeType, title, description, tags,
                breakdown, null);
    }
}
