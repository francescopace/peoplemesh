package org.peoplemesh.util;

import org.junit.jupiter.api.Test;
import org.peoplemesh.domain.dto.GitHubEnrichedResult;
import org.peoplemesh.domain.dto.OidcSubject;
import org.peoplemesh.domain.dto.ProfileSchema;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OAuthProfileParserTest {

    @Test
    void fullNameOrNull_fullNameProvided_returnsIt() {
        OidcSubject subject = new OidcSubject(
                "sub", "Pat Example", null, null, null, null, null, null, null, null);
        assertEquals("Pat Example", OAuthProfileParser.fullNameOrNull(subject));
    }

    @Test
    void fullNameOrNull_firstAndLastProvided_returnsCombined() {
        OidcSubject subject = new OidcSubject(
                "sub", null, "Pat", "Example", null, null, null, null, null, null);
        assertEquals("Pat Example", OAuthProfileParser.fullNameOrNull(subject));
    }

    @Test
    void fullNameOrNull_onlyFirst_returnsFirst() {
        OidcSubject subject = new OidcSubject(
                "sub", null, "Pat", null, null, null, null, null, null, null);
        assertEquals("Pat", OAuthProfileParser.fullNameOrNull(subject));
    }

    @Test
    void fullNameOrNull_onlyLast_returnsLast() {
        OidcSubject subject = new OidcSubject(
                "sub", null, null, "Example", null, null, null, null, null, null);
        assertEquals("Example", OAuthProfileParser.fullNameOrNull(subject));
    }

    @Test
    void fullNameOrNull_allNull_returnsNull() {
        OidcSubject subject = new OidcSubject(
                "sub", null, null, null, null, null, null, null, null, null);
        assertNull(OAuthProfileParser.fullNameOrNull(subject));
    }

    @Test
    void countryFromLocale_extractsCountryCode() {
        assertEquals("IT", OAuthProfileParser.countryFromLocale("it_IT"));
        assertEquals("US", OAuthProfileParser.countryFromLocale("en-US"));
        assertEquals("DE", OAuthProfileParser.countryFromLocale("de_DE"));
    }

    @Test
    void countryFromLocale_nullOrBlank_returnsNull() {
        assertNull(OAuthProfileParser.countryFromLocale(null));
        assertNull(OAuthProfileParser.countryFromLocale(""));
    }

    @Test
    void countryFromLocale_noSeparator_returnsNull() {
        assertNull(OAuthProfileParser.countryFromLocale("en"));
    }

    @Test
    void languageFromLocale_extractsLanguage() {
        assertEquals("Italian", OAuthProfileParser.languageFromLocale("it_IT"));
        assertEquals("English", OAuthProfileParser.languageFromLocale("en-US"));
    }

    @Test
    void languageFromLocale_nullOrBlank_returnsNull() {
        assertNull(OAuthProfileParser.languageFromLocale(null));
        assertNull(OAuthProfileParser.languageFromLocale(""));
    }

    @Test
    void languageFromLocale_plainLanguage_returnsIt() {
        assertEquals("English", OAuthProfileParser.languageFromLocale("en"));
    }

    @Test
    void buildImportSchema_populatesAllFields() {
        OidcSubject subject = new OidcSubject(
                "sub123", "Alice Smith", "Alice", "Smith",
                "alice@example.com", "Senior Java Developer at Acme",
                null, "en-US", "https://img.example/alice.png", "Acme");

        ProfileSchema schema = OAuthProfileParser.buildImportSchema("github", subject);

        assertNotNull(schema);
        assertEquals("1.0", schema.profileVersion());
        assertNotNull(schema.professional());
        assertNull(schema.professional().roles());
        assertNull(schema.professional().seniority());
        assertNull(schema.professional().skillsTechnical());
        assertNotNull(schema.geography());
        assertEquals("US", schema.geography().country());
        assertNotNull(schema.identity());
        assertEquals("Alice Smith", schema.identity().displayName());
        assertEquals("Alice", schema.identity().firstName());
        assertEquals("Smith", schema.identity().lastName());
        assertEquals("alice@example.com", schema.identity().email());
        assertEquals("https://img.example/alice.png", schema.identity().photoUrl());
        assertEquals("Acme", schema.identity().company());
        assertNull(schema.identity().birthDate());
        assertNotNull(schema.fieldProvenance());
        assertEquals("github", schema.fieldProvenance().get("identity.email"));
        assertNull(schema.fieldProvenance().get("professional.skills_technical"));
    }

    @Test
    void buildImportSchema_nullHeadline_setsNullRolesAndSkills() {
        OidcSubject subject = new OidcSubject(
                "sub123", "Bob", null, null,
                null, null, null, null, null, null);

        ProfileSchema schema = OAuthProfileParser.buildImportSchema("google", subject);

        assertNotNull(schema);
        assertNull(schema.professional().roles());
        assertNull(schema.professional().skillsTechnical());
        assertNull(schema.professional().seniority());
    }

    @Test
    void buildImportSchema_withIndustry() {
        OidcSubject subject = new OidcSubject(
                "sub123", "Charlie", null, null,
                null, "DevOps Engineer", "Technology", "de_DE", null, null);

        ProfileSchema schema = OAuthProfileParser.buildImportSchema("linkedin", subject);

        assertNotNull(schema.professional().industries());
        assertTrue(schema.professional().industries().contains("Technology"));
        assertEquals("DE", schema.geography().country());
    }

    @Test
    void buildEnrichedGitHubSchema_mergesRepoLanguagesIntoSkills() {
        OidcSubject subject = new OidcSubject(
                "sub123", "Dev User", "Dev", "User",
                "dev@example.com", "Python developer using Docker", null, "en-US",
                "https://img.example/dev.png", "Acme Inc");

        GitHubEnrichedResult enriched = new GitHubEnrichedResult(
                subject,
                List.of("Java", "TypeScript"),
                List.of("machine-learning", "devops"));

        ProfileSchema schema = OAuthProfileParser.buildEnrichedGitHubSchema(enriched);

        assertNotNull(schema);
        assertNotNull(schema.professional().skillsTechnical());
        assertTrue(schema.professional().skillsTechnical().contains("Java"));
        assertTrue(schema.professional().skillsTechnical().contains("TypeScript"));
        assertNull(schema.professional().roles());
        assertNull(schema.professional().seniority());
        assertNull(schema.professional().toolsAndTech());
        assertNull(schema.interestsProfessional());
        assertNotNull(schema.geography());
        assertEquals("US", schema.geography().country());
        assertNotNull(schema.identity());
        assertEquals("Dev User", schema.identity().displayName());
        assertEquals("Dev", schema.identity().firstName());
        assertEquals("User", schema.identity().lastName());
        assertEquals("dev@example.com", schema.identity().email());
        assertEquals("https://img.example/dev.png", schema.identity().photoUrl());
        assertEquals("Acme Inc", schema.identity().company());
        assertNull(schema.identity().birthDate());
    }

    @Test
    void buildEnrichedGitHubSchema_withoutRepoLabels_keepsInterestsEmpty() {
        OidcSubject subject = new OidcSubject(
                "sub123", "Dev", null, null,
                null, "Kotlin specialist", null, null, null, null);

        GitHubEnrichedResult enriched = new GitHubEnrichedResult(
                subject, null, null);

        ProfileSchema schema = OAuthProfileParser.buildEnrichedGitHubSchema(enriched);

        assertNull(schema.interestsProfessional());
        assertNull(schema.professional().skillsTechnical());
    }

    @Test
    void buildEnrichedGitHubSchema_ignoresRepoLabelsWhenBuildingFallbackSchema() {
        OidcSubject subject = new OidcSubject(
                "sub123", "Dev", null, null,
                null, "Senior Kotlin specialist", null, null, null, null);
        List<String> manyLabels = new ArrayList<>();
        for (int i = 0; i < 80; i++) {
            manyLabels.add("label-" + i);
        }

        GitHubEnrichedResult enriched = new GitHubEnrichedResult(subject, null, manyLabels);

        ProfileSchema schema = OAuthProfileParser.buildEnrichedGitHubSchema(enriched);

        assertNull(schema.professional().skillsTechnical());
        assertNull(schema.professional().roles());
        assertNull(schema.professional().seniority());
        assertNull(schema.professional().toolsAndTech());
        assertNull(schema.interestsProfessional());
    }

    @Test
    void buildImportSchema_truncatesIdentityAndHeadlineDerivedFieldsToContractLimits() {
        String veryLong = "x".repeat(500);
        String longEmail = "a".repeat(340) + "@example.com";
        OidcSubject subject = new OidcSubject(
                "sub123",
                veryLong,
                "f".repeat(200),
                "l".repeat(200),
                longEmail,
                "h".repeat(600),
                "i".repeat(500),
                "en-US",
                "https://example.com/" + "p".repeat(3000),
                "c".repeat(500)
        );

        ProfileSchema schema = OAuthProfileParser.buildImportSchema("github", subject);

        assertNull(schema.professional().roles());
        assertNotNull(schema.professional().industries());
        assertEquals(200, schema.professional().industries().get(0).length());
        assertNull(schema.interestsProfessional());
        assertNotNull(schema.professional().languagesSpoken());
        assertEquals("English", schema.professional().languagesSpoken().get(0));
        assertNotNull(schema.identity());
        assertEquals(200, schema.identity().displayName().length());
        assertEquals(120, schema.identity().firstName().length());
        assertEquals(120, schema.identity().lastName().length());
        assertEquals(320, schema.identity().email().length());
        assertEquals(2048, schema.identity().photoUrl().length());
        assertEquals(200, schema.identity().company().length());
    }

    @Test
    void buildEnrichedGitHubSchema_nullLanguages_keepsSkillsNullInFallback() {
        OidcSubject subject = new OidcSubject(
                "sub123", "Dev", null, null,
                null, "React engineer", null, null, null, null);

        GitHubEnrichedResult enriched = new GitHubEnrichedResult(
                subject, null, List.of("frontend"));

        ProfileSchema schema = OAuthProfileParser.buildEnrichedGitHubSchema(enriched);

        assertNull(schema.professional().skillsTechnical());
    }

    @Test
    void buildEnrichedGitHubSchema_doesNotClassifyRepoLabelsInFallbackParser() {
        OidcSubject subject = new OidcSubject(
                "sub123", "Platform Engineer", null, null,
                null, "Platform Engineer", null, null, null, null);

        GitHubEnrichedResult enriched = new GitHubEnrichedResult(
                subject,
                List.of("Java"),
                List.of("github-actions", "platform-engineering", "semantic-search", "docs", "sample")
        );

        ProfileSchema schema = OAuthProfileParser.buildEnrichedGitHubSchema(enriched);

        assertNotNull(schema.professional().skillsTechnical());
        assertTrue(schema.professional().skillsTechnical().contains("Java"));
        assertNull(schema.professional().toolsAndTech());
        assertNull(schema.interestsProfessional());
    }

    @Test
    void countryFromLocale_underscoreFormat_extracts() {
        assertEquals("FR", OAuthProfileParser.countryFromLocale("fr_FR"));
    }

    @Test
    void countryFromLocale_longCode_returnsNull() {
        assertNull(OAuthProfileParser.countryFromLocale("en-USA"));
    }

    @Test
    void languageFromLocale_singleChar_returnsNull() {
        assertNull(OAuthProfileParser.languageFromLocale("e"));
    }
}
