package org.peoplemesh.domain.dto;

import java.util.List;

public record SearchMatchBreakdown(
        double embeddingScore,
        double mustHaveSkillCoverage,
        double niceToHaveBonus,
        double languageScore,
        double industryScore,
        double geographyScore,
        double finalScore,
        List<String> matchedMustHaveSkills,
        List<String> matchedNiceToHaveSkills,
        List<String> missingMustHaveSkills,
        List<String> reasonCodes,
        String geographyReason
) {}
