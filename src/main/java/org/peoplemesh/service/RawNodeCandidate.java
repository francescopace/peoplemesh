package org.peoplemesh.service;

import org.peoplemesh.domain.enums.NodeType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

record RawNodeCandidate(
        UUID nodeId,
        NodeType nodeType,
        String title,
        String description,
        List<String> tags,
        String country,
        Instant updatedAt,
        Map<String, Object> structuredData,
        double cosineSim
) {
}
