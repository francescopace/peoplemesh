package org.peoplemesh.domain.dto;

import org.peoplemesh.domain.enums.NodeType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record MaintenanceNodeCandidateDto(
        UUID id,
        NodeType nodeType,
        String title,
        String description,
        List<String> tags,
        String country,
        Map<String, Object> structuredData
) {}
