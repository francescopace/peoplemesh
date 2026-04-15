package org.peoplemesh.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ParsedSearchQuery(
        @JsonProperty("must_have") MustHaveFilters mustHave,
        @JsonProperty("nice_to_have") NiceToHaveFilters niceToHave,
        String seniority,
        @JsonProperty("negative_filters") NegativeFilters negativeFilters,
        List<String> keywords,
        @JsonProperty("embedding_text") String embeddingText
) {
    public record MustHaveFilters(
            List<String> skills,
            @JsonProperty("skills_with_level") List<SkillWithLevel> skillsWithLevel,
            List<String> roles,
            List<String> languages,
            List<String> location,
            List<String> industries
    ) {
        public MustHaveFilters(List<String> skills, List<String> roles,
                               List<String> languages, List<String> location,
                               List<String> industries) {
            this(skills, null, roles, languages, location, industries);
        }
    }

    public record NiceToHaveFilters(
            List<String> skills,
            @JsonProperty("skills_with_level") List<SkillWithLevel> skillsWithLevel,
            List<String> industries,
            List<String> experience
    ) {
        public NiceToHaveFilters(List<String> skills, List<String> industries,
                                 List<String> experience) {
            this(skills, null, industries, experience);
        }
    }

    public record NegativeFilters(
            String seniority,
            List<String> skills,
            List<String> location
    ) {}
}
