package org.peoplemesh.util;

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

    private static final int USER_OPTIONAL_SECTION_MAX_CHARS = 1200;

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

        List<String> primary = Stream.of(
                        field("Roles", rolesJoined),
                        list("Technical Skills", professional != null ? professional.skillsTechnical() : null),
                        list("Tools", professional != null ? professional.toolsAndTech() : null),
                        field("Industries", industriesJoined),
                        professional != null && professional.seniority() != null
                                ? "Seniority: " + professional.seniority().name()
                                : null,
                        list("Languages", professional != null ? professional.languagesSpoken() : null),
                        list("Education", personal != null ? personal.education() : null),
                        geography != null && geography.country() != null && !geography.country().isBlank()
                                ? "Country: " + geography.country()
                                : null
                )
                .filter(s -> s != null && !s.isBlank())
                .toList();

        List<String> optional = Stream.of(
                        list("Learning", interestsProfessional != null ? interestsProfessional.learningAreas() : null),
                        list("Projects", interestsProfessional != null ? interestsProfessional.projectTypes() : null),
                        list("Hobbies", personal != null ? personal.hobbies() : null),
                        list("Sports", personal != null ? personal.sports() : null),
                        list("Causes", personal != null ? personal.causes() : null),
                        list("Personality", personal != null ? personal.personalityTags() : null),
                        list("Music", personal != null ? personal.musicGenres() : null),
                        list("Books", personal != null ? personal.bookGenres() : null),
                        professional != null && professional.workModePreference() != null
                                ? "Work Mode: " + professional.workModePreference().name()
                                : null,
                        professional != null && professional.employmentType() != null
                                ? "Employment: " + professional.employmentType().name()
                                : null,
                        list("Soft Skills", professional != null ? professional.skillsSoft() : null)
                )
                .filter(s -> s != null && !s.isBlank())
                .toList();

        return joinWithOptional(primary, optional, USER_OPTIONAL_SECTION_MAX_CHARS);
    }

    private static String buildUserText(MeshNode node) {
        Map<String, Object> sd = node.structuredData != null ? node.structuredData : Collections.emptyMap();
        List<String> primary = Stream.of(
                        field("Roles", node.description),
                        list("Technical Skills", node.tags),
                        list("Tools", sdList(sd, "tools_and_tech")),
                        field("Industries", sdString(sd, "industries")),
                        sdString(sd, "seniority") != null ? "Seniority: " + sdString(sd, "seniority") : null,
                        list("Languages", sdList(sd, "languages_spoken")),
                        list("Education", sdList(sd, "education")),
                        node.country != null ? "Country: " + node.country : null
                )
                .filter(s -> s != null && !s.isBlank())
                .toList();

        List<String> optional = Stream.of(
                        list("Learning", sdList(sd, "learning_areas")),
                        list("Projects", sdList(sd, "project_types")),
                        sdString(sd, "work_mode") != null ? "Work Mode: " + sdString(sd, "work_mode") : null,
                        sdString(sd, "employment_type") != null ? "Employment: " + sdString(sd, "employment_type") : null,
                        list("Soft Skills", sdList(sd, "skills_soft"))
                )
                .filter(s -> s != null && !s.isBlank())
                .toList();

        return joinWithOptional(primary, optional, USER_OPTIONAL_SECTION_MAX_CHARS);
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

    private static String joinWithOptional(List<String> primary, List<String> optional, int maxChars) {
        List<String> parts = new ArrayList<>(primary);
        int currentLength = String.join(". ", parts).length();
        for (String section : optional) {
            int candidateLength = currentLength == 0
                    ? section.length()
                    : currentLength + 2 + section.length();
            if (candidateLength > maxChars) {
                break;
            }
            parts.add(section);
            currentLength = candidateLength;
        }
        return String.join(". ", parts);
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
