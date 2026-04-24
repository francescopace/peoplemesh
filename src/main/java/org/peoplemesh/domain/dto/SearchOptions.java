package org.peoplemesh.domain.dto;

public record SearchOptions(
        Double weightEmbedding,
        Double weightMustHave,
        Double weightNiceToHave,
        Double weightLanguage,
        Double weightGeography,
        Double weightIndustry,
        Double weightSeniority,
        Double weightGenericKeyword,
        Double skillMatchThreshold,
        Integer profileNiceSkillsCap,
        Boolean profileIncludeNiceToHave,
        Boolean profileIncludeInterestsInEmbeddingText
) {
    public boolean hasOverrides() {
        return weightEmbedding != null
                || weightMustHave != null
                || weightNiceToHave != null
                || weightLanguage != null
                || weightGeography != null
                || weightIndustry != null
                || weightSeniority != null
                || weightGenericKeyword != null
                || skillMatchThreshold != null
                || profileNiceSkillsCap != null
                || profileIncludeNiceToHave != null
                || profileIncludeInterestsInEmbeddingText != null;
    }
}
