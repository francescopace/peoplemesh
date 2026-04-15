package org.peoplemesh.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record SkillAssessmentDto(
        @JsonProperty("skill_id") UUID skillId,
        @JsonProperty("skill_name") String skillName,
        String category,
        int level,
        boolean interest,
        String source,
        @JsonProperty("match_type") String matchType,
        double confidence
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
