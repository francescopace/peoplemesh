package org.peoplemesh.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SearchQuery(
        @JsonProperty("must_have") @Valid MustHaveFilters mustHave,
        @JsonProperty("nice_to_have") @Valid NiceToHaveFilters niceToHave,
        @Pattern(regexp = "^(?i)(junior|mid|senior|lead|unknown)$")
        String seniority,
        @JsonProperty("negative_filters") @Valid NegativeFilters negativeFilters,
        @Size(max = 30) List<@Size(max = 80) String> keywords,
        @Size(max = 500)
        @JsonProperty("embedding_text") String embeddingText,
        @Pattern(regexp = "^(?i)(all|people|jobs|communities|events|projects|groups|unknown)$")
        @JsonProperty("result_scope") String resultScope
) {
    public record MustHaveFilters(
            @Size(max = 50) List<@Size(max = 80) String> skills,
            @JsonProperty("skills_with_level") @Size(max = 50) List<@Valid SkillWithLevel> skillsWithLevel,
            @Size(max = 20) List<@Size(max = 80) String> roles,
            @Size(max = 20) List<@Size(max = 80) String> languages,
            @Size(max = 20) List<@Size(max = 80) String> location,
            @Size(max = 20) List<@Size(max = 80) String> industries
    ) {
        public MustHaveFilters(List<String> skills, List<String> roles,
                               List<String> languages, List<String> location,
                               List<String> industries) {
            this(skills, null, roles, languages, location, industries);
        }
    }

    public record NiceToHaveFilters(
            @Size(max = 50) List<@Size(max = 80) String> skills,
            @JsonProperty("skills_with_level") @Size(max = 50) List<@Valid SkillWithLevel> skillsWithLevel,
            @Size(max = 20) List<@Size(max = 80) String> industries,
            @Size(max = 20) List<@Size(max = 80) String> experience
    ) {
        public NiceToHaveFilters(List<String> skills, List<String> industries,
                                 List<String> experience) {
            this(skills, null, industries, experience);
        }
    }

    public record NegativeFilters(
            @Pattern(regexp = "^(?i)(junior|mid|senior|lead|unknown)$")
            String seniority,
            @Size(max = 20) List<@Size(max = 80) String> skills,
            @Size(max = 20) List<@Size(max = 80) String> location
    ) {}
}
