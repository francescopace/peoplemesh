package org.peoplemesh.domain.dto;

import java.util.List;

public record JobMatchBreakdown(
        double embeddingScore,
        double skillsScore,
        double employmentScore,
        double geographyScore,
        double rawScore,
        double decayMultiplier,
        double finalScore,
        List<String> matchedSkills,
        String geographyReason,
        List<String> reasonCodes
) {}
