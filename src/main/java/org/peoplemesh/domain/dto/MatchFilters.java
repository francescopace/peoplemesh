package org.peoplemesh.domain.dto;

import org.peoplemesh.domain.enums.*;

import java.util.List;

public record MatchFilters(
        List<String> skillsTechnical,
        List<CollaborationGoal> collaborationGoals,
        WorkMode workMode,
        EmploymentType employmentType,
        String country
) {}
