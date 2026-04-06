package org.peoplemesh.domain.dto;

import org.peoplemesh.domain.enums.*;

import java.util.List;
import java.util.UUID;

public record MatchResult(
        UUID profileId,
        double score,
        Seniority seniority,
        List<String> skillsTechnical,
        List<String> toolsAndTech,
        List<String> skillsSoft,
        WorkMode workMode,
        EmploymentType employmentType,
        List<CollaborationGoal> collaborationGoals,
        List<String> topicsFrequent,
        List<String> learningAreas,
        String country,
        String timezone,
        MatchScoreBreakdown breakdown
) {}
