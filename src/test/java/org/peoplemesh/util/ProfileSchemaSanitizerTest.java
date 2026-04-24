package org.peoplemesh.util;

import org.junit.jupiter.api.Test;
import org.peoplemesh.domain.dto.ProfileSchema;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProfileSchemaSanitizerTest {

    @Test
    void sanitizeStructuredSchema_normalizesListsAndGeography() {
        ProfileSchema source = new ProfileSchema(
                "1.0",
                null,
                null,
                new ProfileSchema.ProfessionalInfo(
                        List.of("Commercial Account Executive"),
                        null,
                        List.of("Information Technology"),
                        List.of("Java"),
                        null,
                        List.of("GitHub", "KubeCon Conference", "Terraform"),
                        List.of("English"),
                        null,
                        null
                ),
                null,
                new ProfileSchema.InterestsInfo(
                        List.of("DevOps practices"),
                        List.of("platform engineering")
                ),
                null,
                new ProfileSchema.GeographyInfo("italy", "Remote - Rome", null),
                null,
                null
        );

        ProfileSchema sanitized = ProfileSchemaSanitizer.sanitizeStructuredSchema(source);

        assertNotNull(sanitized.professional());
        assertEquals(List.of("GitHub", "KubeCon Conference", "Terraform"), sanitized.professional().toolsAndTech());
        assertNotNull(sanitized.geography());
        assertNull(sanitized.geography().country());
        assertEquals("Remote - Rome", sanitized.geography().city());
    }

    @Test
    void sanitizeStructuredSchema_keepsProvidedRoles() {
        ProfileSchema source = new ProfileSchema(
                "1.0",
                null,
                null,
                new ProfileSchema.ProfessionalInfo(
                        List.of("Pre-sales", "Developer Advocate", "Account Management"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ),
                null,
                null,
                null,
                null,
                null,
                null
        );

        ProfileSchema sanitized = ProfileSchemaSanitizer.sanitizeStructuredSchema(source);

        assertNotNull(sanitized.professional());
        assertEquals(List.of("Pre-sales", "Developer Advocate", "Account Management"), sanitized.professional().roles());
    }
}
