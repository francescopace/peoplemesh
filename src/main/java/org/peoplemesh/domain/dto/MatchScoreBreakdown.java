package org.peoplemesh.domain.dto;

import org.peoplemesh.domain.enums.CollaborationGoal;

import java.util.List;

public record MatchScoreBreakdown(
        double embeddingScore,
        double skillsScore,
        double goalsScore,
        double geographyScore,
        double rawScore,
        double decayMultiplier,
        double finalScore,
        List<String> matchedSkills,
        List<CollaborationGoal> matchedGoals,
        String geographyReason,
        List<String> reasonCodes
) {}
