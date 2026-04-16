package org.peoplemesh.service;

import org.peoplemesh.domain.dto.JobPostingDto;
import org.peoplemesh.domain.dto.ProfileSchema;
import org.peoplemesh.domain.model.MeshNode;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.peoplemesh.util.StructuredDataUtils.sdListOrNull;
import static org.peoplemesh.util.StructuredDataUtils.sdString;

/**
 * Builds embedding text for any MeshNode type (USER, JOB, COMMUNITY, etc.).
 * Centralises the three former variants: ProfileService.nodeToEmbeddingText,
 * NodeService.nodeToText, and JobService.jobNodeToText.
 */
public final class EmbeddingTextBuilder {

    private EmbeddingTextBuilder() {}

    public static String buildText(MeshNode node) {
        return switch (node.nodeType) {
            case USER -> buildUserText(node);
            case JOB -> buildJobText(node);
            default -> buildGenericText(node);
        };
    }

    public static String buildFromSchema(ProfileSchema schema) {
        if (schema == null) {
            return "";
        }
        ProfileSchema.ProfessionalInfo professional = schema.professional();
        ProfileSchema.InterestsInfo interestsProfessional = schema.interestsProfessional();
        ProfileSchema.PersonalInfo personal = schema.personal();
        ProfileSchema.GeographyInfo geography = schema.geography();

        String rolesJoined = professional != null && professional.roles() != null && !professional.roles().isEmpty()
                ? String.join(", ", professional.roles())
                : null;
        String industriesJoined = professional != null && professional.industries() != null && !professional.industries().isEmpty()
                ? String.join(", ", professional.industries())
                : null;

        return Stream.of(
                        field("Roles", rolesJoined),
                        professional != null && professional.seniority() != null
                                ? "Seniority: " + professional.seniority().name()
                                : null,
                        field("Industries", industriesJoined),
                        list("Technical Skills", professional != null ? professional.skillsTechnical() : null),
                        list("Soft Skills", professional != null ? professional.skillsSoft() : null),
                        list("Tools", professional != null ? professional.toolsAndTech() : null),
                        list("Languages", professional != null ? professional.languagesSpoken() : null),
                        professional != null && professional.workModePreference() != null
                                ? "Work Mode: " + professional.workModePreference().name()
                                : null,
                        professional != null && professional.employmentType() != null
                                ? "Employment: " + professional.employmentType().name()
                                : null,
                        list("Topics", interestsProfessional != null ? interestsProfessional.topicsFrequent() : null),
                        list("Learning", interestsProfessional != null ? interestsProfessional.learningAreas() : null),
                        list("Projects", interestsProfessional != null ? interestsProfessional.projectTypes() : null),
                        list("Hobbies", personal != null ? personal.hobbies() : null),
                        list("Sports", personal != null ? personal.sports() : null),
                        list("Education", personal != null ? personal.education() : null),
                        list("Causes", personal != null ? personal.causes() : null),
                        list("Personality", personal != null ? personal.personalityTags() : null),
                        list("Music", personal != null ? personal.musicGenres() : null),
                        list("Books", personal != null ? personal.bookGenres() : null),
                        geography != null && geography.country() != null && !geography.country().isBlank()
                                ? "Country: " + geography.country()
                                : null
                )
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(". "));
    }

    private static String buildUserText(MeshNode node) {
        Map<String, Object> sd = node.structuredData != null ? node.structuredData : Collections.emptyMap();
        return Stream.of(
                        field("Roles", node.description),
                        sdString(sd, "seniority") != null ? "Seniority: " + sdString(sd, "seniority") : null,
                        field("Industries", sdString(sd, "industries")),
                        list("Technical Skills", node.tags),
                        list("Soft Skills", sdList(sd, "skills_soft")),
                        list("Tools", sdList(sd, "tools_and_tech")),
                        list("Languages", sdList(sd, "languages_spoken")),
                        sdString(sd, "work_mode") != null ? "Work Mode: " + sdString(sd, "work_mode") : null,
                        sdString(sd, "employment_type") != null ? "Employment: " + sdString(sd, "employment_type") : null,
                        list("Topics", sdList(sd, "topics_frequent")),
                        list("Learning", sdList(sd, "learning_areas")),
                        list("Projects", sdList(sd, "project_types")),
                        list("Hobbies", sdList(sd, "hobbies")),
                        list("Sports", sdList(sd, "sports")),
                        list("Education", sdList(sd, "education")),
                        list("Causes", sdList(sd, "causes")),
                        list("Personality", sdList(sd, "personality_tags")),
                        list("Music", sdList(sd, "music_genres")),
                        list("Books", sdList(sd, "book_genres")),
                        node.country != null ? "Country: " + node.country : null
                )
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(". "));
    }

    private static String buildJobText(MeshNode node) {
        JobPostingDto view = JobPostingDto.fromMeshNode(node);
        return Stream.of(
                        "Title: " + node.title,
                        "Description: " + node.description,
                        field("Requirements", view.requirementsText()),
                        view.skillsRequired() == null || view.skillsRequired().isEmpty()
                                ? null
                                : "Required Skills: " + String.join(", ", view.skillsRequired()),
                        view.workMode() != null ? "Work Mode: " + view.workMode() : null,
                        view.employmentType() != null ? "Employment: " + view.employmentType() : null,
                        field("Country", node.country)
                )
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(". "));
    }

    private static String buildGenericText(MeshNode node) {
        Stream.Builder<String> parts = Stream.builder();
        parts.add("Type: " + node.nodeType);
        parts.add("Title: " + node.title);
        if (node.description != null && !node.description.isBlank()) {
            parts.add("Description: " + node.description);
        }
        if (node.tags != null && !node.tags.isEmpty()) {
            parts.add("Tags: " + String.join(", ", node.tags));
        }
        if (node.country != null) {
            parts.add("Country: " + node.country);
        }
        if (node.structuredData != null) {
            node.structuredData.forEach((k, v) -> {
                if (v != null && !v.toString().isBlank() && !"[]".equals(v.toString())) {
                    parts.add(k.replace("_", " ") + ": " + v);
                }
            });
        }
        return parts.build()
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(". "));
    }

    private static List<String> sdList(Map<String, Object> sd, String key) {
        return sdListOrNull(sd, key);
    }

    private static String field(String label, String value) {
        if (value == null || value.isBlank()) return null;
        return label + ": " + value;
    }

    private static String list(String label, List<String> items) {
        if (items == null || items.isEmpty()) return null;
        return label + ": " + String.join(", ", items);
    }
}
