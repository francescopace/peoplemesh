package org.peoplemesh.util;

import org.peoplemesh.domain.dto.ProfileSchema;

import java.util.List;
import java.util.Locale;

public final class ProfileSchemaSanitizer {

    private ProfileSchemaSanitizer() {}

    public static ProfileSchema sanitizeStructuredSchema(ProfileSchema schema) {
        if (schema == null || schema.professional() == null) {
            return schema;
        }
        var p = schema.professional();
        var sanitizedTools = sanitizeToolsAndTech(p.toolsAndTech());
        var sanitizedRoles = sanitizeRoles(p.roles());
        var sanitizedIndustries = sanitizeIndustries(p.industries());
        var sanitizedProjectTypes = sanitizeProjectTypes(
                schema.interestsProfessional() != null ? schema.interestsProfessional().projectTypes() : null
        );
        var sanitizedLearningAreas = sanitizeLearningAreas(
                schema.interestsProfessional() != null ? schema.interestsProfessional().learningAreas() : null
        );
        var sanitizedGeography = sanitizeGeography(schema.geography());
        var sanitizedProfessional = new ProfileSchema.ProfessionalInfo(
                sanitizedRoles,
                p.seniority(),
                sanitizedIndustries,
                p.skillsTechnical(),
                p.skillsSoft(),
                sanitizedTools,
                p.languagesSpoken(),
                p.workModePreference(),
                p.employmentType()
        );
        return new ProfileSchema(
                schema.profileVersion(),
                schema.generatedAt(),
                schema.consent(),
                sanitizedProfessional,
                schema.contacts(),
                sanitizedLearningAreas == null && sanitizedProjectTypes == null
                        ? null
                        : new ProfileSchema.InterestsInfo(sanitizedLearningAreas, sanitizedProjectTypes),
                schema.personal(),
                sanitizedGeography,
                schema.fieldProvenance(),
                schema.identity()
        );
    }

    static List<String> sanitizeToolsAndTech(List<String> tools) {
        return ProfileSchemaNormalization.normalizeList(
                tools,
                ProfileSchema.MAX_TOOLS_AND_TECH,
                ProfileSchema.MAX_TOOL_AND_TECH_LENGTH
        );
    }

    static List<String> sanitizeRoles(List<String> roles) {
        return ProfileSchemaNormalization.normalizeList(roles, ProfileSchema.MAX_ROLES, ProfileSchema.MAX_ROLE_LENGTH);
    }

    static List<String> sanitizeIndustries(List<String> industries) {
        return ProfileSchemaNormalization.normalizeList(
                industries,
                ProfileSchema.MAX_INDUSTRIES,
                ProfileSchema.MAX_INDUSTRY_LENGTH
        );
    }

    static List<String> sanitizeProjectTypes(List<String> projectTypes) {
        return ProfileSchemaNormalization.normalizeList(
                projectTypes,
                ProfileSchema.MAX_PROJECT_TYPES,
                ProfileSchema.MAX_PROJECT_TYPE_LENGTH
        );
    }

    static List<String> sanitizeLearningAreas(List<String> learningAreas) {
        return ProfileSchemaNormalization.normalizeList(
                learningAreas,
                ProfileSchema.MAX_LEARNING_AREAS,
                ProfileSchema.MAX_LEARNING_AREA_LENGTH
        );
    }

    static ProfileSchema.GeographyInfo sanitizeGeography(ProfileSchema.GeographyInfo geography) {
        if (geography == null) {
            return null;
        }
        String country = geography.country();
        if (country != null) {
            String normalizedCountry = country.trim().toUpperCase(Locale.ROOT);
            country = normalizedCountry.matches("^[A-Z]{2}$") ? normalizedCountry : null;
        }
        String city = geography.city();
        if (city != null) {
            String normalizedCity = city.trim();
            city = normalizedCity.isEmpty() ? null : normalizedCity;
        }
        return new ProfileSchema.GeographyInfo(country, city, geography.timezone());
    }
}
