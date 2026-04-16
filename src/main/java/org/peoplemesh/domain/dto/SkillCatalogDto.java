package org.peoplemesh.domain.dto;

import java.util.Map;
import java.util.UUID;

public record SkillCatalogDto(
        UUID id,
        String name,
        String description,
        Map<String, Object> levelScale,
        String source,
        long skillCount,
        String createdAt,
        String updatedAt
) {
}
