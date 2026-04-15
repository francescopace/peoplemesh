package org.peoplemesh.domain.dto;

import org.peoplemesh.domain.enums.NodeType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record NodeDto(
        UUID id,
        NodeType nodeType,
        String title,
        String description,
        List<String> tags,
        Map<String, Object> structuredData,
        String country,
        Instant createdAt,
        Instant updatedAt
) {}
