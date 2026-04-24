package org.peoplemesh.domain.dto;

import java.util.List;
import java.util.UUID;

public record SkillDefinitionDto(
        UUID id,
        String name,
        List<String> aliases,
        int usageCount,
        boolean hasEmbedding
) {
}
