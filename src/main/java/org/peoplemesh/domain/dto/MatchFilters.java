package org.peoplemesh.domain.dto;

import org.peoplemesh.domain.enums.EmploymentType;
import org.peoplemesh.domain.enums.WorkMode;

import java.util.List;

public record MatchFilters(
        List<String> skillsTechnical,
        WorkMode workMode,
        EmploymentType employmentType,
        String country,
        List<SkillWithLevel> skillsWithLevel
) {
    public MatchFilters(List<String> skillsTechnical,
                        WorkMode workMode, EmploymentType employmentType, String country) {
        this(skillsTechnical, workMode, employmentType, country, null);
    }
}
