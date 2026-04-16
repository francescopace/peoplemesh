package org.peoplemesh.domain.dto;

import java.util.List;
import java.util.UUID;

public record SkillDefinitionDto(
        UUID id,
        String category,
        String name,
        List<String> aliases,
        String lxpRecommendation,
        boolean hasEmbedding
) {
}
