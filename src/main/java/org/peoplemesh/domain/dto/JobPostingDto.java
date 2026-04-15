package org.peoplemesh.domain.dto;

import org.peoplemesh.domain.enums.EmploymentType;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.enums.WorkMode;
import org.peoplemesh.domain.model.MeshNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record JobPostingDto(
        UUID id,
        String title,
        String description,
        String requirementsText,
        List<String> skillsRequired,
        WorkMode workMode,
        EmploymentType employmentType,
        String country,
        String externalUrl,
        Instant createdAt,
        Instant updatedAt,
        Instant publishedAt
) {
    public static JobPostingDto fromMeshNode(MeshNode n) {
        if (n.nodeType != NodeType.JOB) {
            throw new IllegalArgumentException("Mesh node is not a job");
        }
        return fromParts(
                n.id,
                n.title,
                n.description,
                n.country,
                n.structuredData,
                n.tags,
                n.createdAt,
                n.updatedAt,
                n.closedAt == null ? n.createdAt : null
        );
    }

    public static JobPostingDto fromNodeDto(NodeDto d) {
        if (d.nodeType() != NodeType.JOB) {
            throw new IllegalArgumentException("Mesh node is not a job");
        }
        return fromParts(
                d.id(),
                d.title(),
                d.description(),
                d.country(),
                d.structuredData(),
                d.tags(),
                d.createdAt(),
                d.updatedAt(),
                d.createdAt()
        );
    }

    private static JobPostingDto fromParts(
            UUID id,
            String title,
            String description,
            String country,
            Map<String, Object> structuredData,
            List<String> tags,
            Instant createdAt,
            Instant updatedAt,
            Instant publishedAt
    ) {
        String requirementsText = stringProp(structuredData, "requirements_text");
        List<String> skills = (tags != null && !tags.isEmpty())
                ? List.copyOf(tags)
                : stringListProp(structuredData, "skills_required");
        WorkMode workMode = enumProp(structuredData, "work_mode", WorkMode.class);
        EmploymentType employmentType = enumProp(structuredData, "employment_type", EmploymentType.class);
        String externalUrl = stringProp(structuredData, "external_url");
        return new JobPostingDto(
                id,
                title,
                description,
                requirementsText,
                skills,
                workMode,
                employmentType,
                country,
                externalUrl,
                createdAt,
                updatedAt,
                publishedAt
        );
    }

    private static String stringProp(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object v = map.get(key);
        return v == null ? null : v.toString();
    }

    private static List<String> stringListProp(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object v = map.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object o : list) {
                if (o != null) {
                    out.add(o.toString());
                }
            }
            return out.isEmpty() ? null : out;
        }
        return null;
    }

    @SuppressWarnings("null")
    private static <E extends Enum<E>> E enumProp(Map<String, Object> map, String key, Class<E> type) {
        if (map == null) {
            return null;
        }
        Object v = map.get(key);
        if (v == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, v.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
