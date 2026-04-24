package org.peoplemesh.domain.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Unified match result for all node types including people.
 * People are represented as nodeType "PEOPLE".
 */
public record MeshMatchResult(
        UUID id,
        String nodeType,
        String title,
        String description,
        String avatarUrl,
        List<String> tags,
        String country,
        double score,
        MeshMatchBreakdown breakdown,
        PersonDetails person
) {

    /**
     * Extra fields only present for PEOPLE matches.
     */
    public record PersonDetails(
            List<String> roles,
            String seniority,
            List<String> skillsTechnical,
            List<String> toolsAndTech,
            List<String> skillsSoft,
            String workMode,
            String employmentType,
            List<String> learningAreas,
            List<String> hobbies,
            List<String> sports,
            List<String> causes,
            String city,
            String timezone,
            String slackHandle,
            String email,
            String telegramHandle,
            String mobilePhone,
            String linkedinUrl,
            Map<String, Integer> skillLevels
    ) {}

    public record MeshMatchBreakdown(
            double embeddingScore,
            double overlapScore,
            double niceToHaveScore,
            double languageScore,
            double industryScore,
            double geographyScore,
            double rawScore,
            double decayMultiplier,
            double finalScore,
            List<String> commonItems,
            String geographyReason,
            List<String> matchedMustHaveSkills,
            List<String> matchedNiceToHaveSkills,
            List<String> missingMustHaveSkills,
            List<String> reasonCodes,
            boolean mustHaveRequested,
            boolean niceToHaveRequested,
            boolean languageRequested,
            boolean industryRequested,
            boolean geographyRequested,
            Double mustHavePenaltyFactor,
            Double negativeSkillsPenaltyFactor,
            Double seniorityScore,
            Double keywordScore,
            Double weightEmbedding,
            Double weightMustHave,
            Double weightNiceToHave,
            Double weightLanguage,
            Double weightIndustry,
            Double weightGeography,
            Double weightSeniority,
            Double weightKeyword
    ) {}
}
