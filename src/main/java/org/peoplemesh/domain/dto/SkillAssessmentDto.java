package org.peoplemesh.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record SkillAssessmentDto(
        @NotNull @JsonProperty("skill_id") UUID skillId,
        @JsonProperty("skill_name") @Size(max = 200) String skillName,
        @Size(max = 100) String category,
        @Min(0) @Max(5) int level,
        boolean interest,
        @Size(max = 50) String source,
        @JsonProperty("match_type") @Size(max = 50) String matchType,
        @Min(0) @Max(1) double confidence
) {
    public static SkillAssessmentDto forInput(UUID skillId, int level, boolean interest) {
        return new SkillAssessmentDto(skillId, null, null, level, interest, "SELF", null, 0);
    }

    public static SkillAssessmentDto fromAssessment(UUID skillId, String name, String category,
                                                     int level, boolean interest, String source) {
        return new SkillAssessmentDto(skillId, name, category, level, interest, source, null, 0);
    }

    public static SkillAssessmentDto suggestion(UUID skillId, String name, String category,
                                                 String matchType, double confidence) {
        return new SkillAssessmentDto(skillId, name, category, 0, false, null, matchType, confidence);
    }
}
