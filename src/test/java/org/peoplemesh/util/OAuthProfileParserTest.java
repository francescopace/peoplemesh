package org.peoplemesh.util;

import org.junit.jupiter.api.Test;
import org.peoplemesh.domain.dto.GitHubEnrichedResult;
import org.peoplemesh.domain.dto.OidcSubject;
import org.peoplemesh.domain.dto.ProfileSchema;
import org.peoplemesh.domain.enums.Seniority;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OAuthProfileParserTest {

    @Test
    void inferSeniority_executiveKeywords() {
        assertEquals(Seniority.EXECUTIVE, OAuthProfileParser.inferSeniority("Founder & CEO at Acme"));
        assertEquals(Seniority.EXECUTIVE, OAuthProfileParser.inferSeniority("CTO at Startup"));
        assertEquals(Seniority.EXECUTIVE, OAuthProfileParser.inferSeniority("VP Engineering"));
        assertEquals(Seniority.EXECUTIVE, OAuthProfileParser.inferSeniority("Head of Product"));
        assertEquals(Seniority.EXECUTIVE, OAuthProfileParser.inferSeniority("Chief Architect"));
    }

    @Test
    void inferSeniority_leadKeywords() {
        assertEquals(Seniority.LEAD, OAuthProfileParser.inferSeniority("Lead Engineer at Google"));
        assertEquals(Seniority.LEAD, OAuthProfileParser.inferSeniority("Principal Developer"));
        assertEquals(Seniority.LEAD, OAuthProfileParser.inferSeniority("Staff Software Engineer"));
    }

    @Test
    void inferSeniority_seniorKeywords() {
        assertEquals(Seniority.SENIOR, OAuthProfileParser.inferSeniority("Senior Backend Engineer"));
        assertEquals(Seniority.SENIOR, OAuthProfileParser.inferSeniority("Sr Engineer at Meta"));
    }

    @Test
    void inferSeniority_juniorKeywords() {
        assertEquals(Seniority.JUNIOR, OAuthProfileParser.inferSeniority("Junior Developer"));
        assertEquals(Seniority.JUNIOR, OAuthProfileParser.inferSeniority("Intern at StartupXYZ"));
        assertEquals(Seniority.JUNIOR, OAuthProfileParser.inferSeniority("Entry level analyst"));
    }

    @Test
    void inferSeniority_defaultMid() {
        assertEquals(Seniority.MID, OAuthProfileParser.inferSeniority("Software Engineer at Acme"));
    }

    @Test
    void inferSeniority_null_returnsNull() {
        assertNull(OAuthProfileParser.inferSeniority(null));
        assertNull(OAuthProfileParser.inferSeniority(""));
        assertNull(OAuthProfileParser.inferSeniority("  "));
    }

    @Test
    void normalizeRoleFromHeadline_extractsBeforeSeparator() {
        assertEquals("Software Engineer", OAuthProfileParser.normalizeRoleFromHeadline("Software Engineer at Google"));
        assertEquals("Backend Dev", OAuthProfileParser.normalizeRoleFromHeadline("Backend Dev | Google"));
        assertEquals("DevOps Engineer", OAuthProfileParser.normalizeRoleFromHeadline("DevOps Engineer - Amazon"));
    }

    @Test
    void normalizeRoleFromHeadline_nullOrBlank_returnsNull() {
        assertNull(OAuthProfileParser.normalizeRoleFromHeadline(null));
        assertNull(OAuthProfileParser.normalizeRoleFromHeadline(""));
        assertNull(OAuthProfileParser.normalizeRoleFromHeadline("   "));
    }

    @Test
    void extractKnownSkills_findsKnownSkills() {
        List<String> skills = OAuthProfileParser.extractKnownSkills("Java and Python developer using Docker");
        assertTrue(skills.contains("Java"));
        assertTrue(skills.contains("Python"));
        assertTrue(skills.contains("Docker"));
    }

    @Test
    void extractKnownSkills_caseInsensitive() {
        List<String> skills = OAuthProfileParser.extractKnownSkills("JAVA spring kubernetes");
        assertTrue(skills.contains("Java"));
        assertTrue(skills.contains("Spring"));
        assertTrue(skills.contains("Kubernetes"));
    }

    @Test
    void extractKnownSkills_nullOrBlank_returnsEmpty() {
        assertTrue(OAuthProfileParser.extractKnownSkills(null).isEmpty());
        assertTrue(OAuthProfileParser.extractKnownSkills("").isEmpty());
    }

    @Test
    void extractKnownSkills_noDuplicates() {
        List<String> skills = OAuthProfileParser.extractKnownSkills("java Java JAVA developer");
        assertEquals(1, skills.stream().filter(s -> s.equalsIgnoreCase("Java")).count());
    }

    @Test
    void extractKnownSkills_reactAwsSqlAndMore() {
        List<String> skills = OAuthProfileParser.extractKnownSkills(
                "React engineer on AWS; SQL via PostgreSQL, TypeScript, Terraform");
        assertTrue(skills.contains("React"));
        assertTrue(skills.contains("AWS"));
        assertTrue(skills.contains("PostgreSQL"));
        assertTrue(skills.contains("TypeScript"));
        assertTrue(skills.contains("Terraform"));
    }

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
        assertEquals("it", OAuthProfileParser.languageFromLocale("it_IT"));
        assertEquals("en", OAuthProfileParser.languageFromLocale("en-US"));
    }

    @Test
    void languageFromLocale_nullOrBlank_returnsNull() {
        assertNull(OAuthProfileParser.languageFromLocale(null));
        assertNull(OAuthProfileParser.languageFromLocale(""));
    }

    @Test
    void languageFromLocale_plainLanguage_returnsIt() {
        assertEquals("en", OAuthProfileParser.languageFromLocale("en"));
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
        assertNotNull(schema.professional().roles());
        assertFalse(schema.professional().roles().isEmpty());
        assertEquals(Seniority.SENIOR, schema.professional().seniority());
        assertNotNull(schema.professional().skillsTechnical());
        assertTrue(schema.professional().skillsTechnical().contains("Java"));
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
        assertEquals("github", schema.fieldProvenance().get("professional.skills_technical"));
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
    void buildEnrichedGitHubSchema_mergesLanguagesAndTopics() {
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
        assertTrue(schema.professional().skillsTechnical().contains("Python"));
        assertTrue(schema.professional().skillsTechnical().contains("Docker"));
        assertTrue(schema.professional().skillsTechnical().contains("Java"));
        assertTrue(schema.professional().skillsTechnical().contains("TypeScript"));
        assertNotNull(schema.interestsProfessional());
        assertNotNull(schema.interestsProfessional().topicsFrequent());
        assertTrue(schema.interestsProfessional().topicsFrequent().contains("machine-learning"));
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
    void buildEnrichedGitHubSchema_noTopics_fallsBackToHeadline() {
        OidcSubject subject = new OidcSubject(
                "sub123", "Dev", null, null,
                null, "Kotlin specialist", null, null, null, null);

        GitHubEnrichedResult enriched = new GitHubEnrichedResult(
                subject, null, null);

        ProfileSchema schema = OAuthProfileParser.buildEnrichedGitHubSchema(enriched);

        assertNotNull(schema.interestsProfessional().topicsFrequent());
        assertTrue(schema.interestsProfessional().topicsFrequent().get(0).contains("Kotlin"));
    }

    @Test
    void buildEnrichedGitHubSchema_limitsTopicsToSchemaMax() {
        OidcSubject subject = new OidcSubject(
                "sub123", "Dev", null, null,
                null, "Kotlin specialist", null, null, null, null);
        List<String> manyTopics = new ArrayList<>();
        for (int i = 0; i < 80; i++) {
            manyTopics.add("topic-" + i);
        }

        GitHubEnrichedResult enriched = new GitHubEnrichedResult(subject, null, manyTopics);

        ProfileSchema schema = OAuthProfileParser.buildEnrichedGitHubSchema(enriched);

        assertNotNull(schema.interestsProfessional().topicsFrequent());
        assertEquals(50, schema.interestsProfessional().topicsFrequent().size());
        assertEquals("topic-0", schema.interestsProfessional().topicsFrequent().get(0));
        assertEquals("topic-49", schema.interestsProfessional().topicsFrequent().get(49));
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

        assertNotNull(schema.professional().roles());
        assertEquals(200, schema.professional().roles().get(0).length());
        assertNotNull(schema.professional().industries());
        assertEquals(200, schema.professional().industries().get(0).length());
        assertNotNull(schema.interestsProfessional().topicsFrequent());
        assertEquals(200, schema.interestsProfessional().topicsFrequent().get(0).length());
        assertNotNull(schema.identity());
        assertEquals(200, schema.identity().displayName().length());
        assertEquals(120, schema.identity().firstName().length());
        assertEquals(120, schema.identity().lastName().length());
        assertEquals(320, schema.identity().email().length());
        assertEquals(2048, schema.identity().photoUrl().length());
        assertEquals(200, schema.identity().company().length());
    }

    @Test
    void buildEnrichedGitHubSchema_nullLanguages_usesOnlyHeadlineSkills() {
        OidcSubject subject = new OidcSubject(
                "sub123", "Dev", null, null,
                null, "React engineer", null, null, null, null);

        GitHubEnrichedResult enriched = new GitHubEnrichedResult(
                subject, null, List.of("frontend"));

        ProfileSchema schema = OAuthProfileParser.buildEnrichedGitHubSchema(enriched);

        assertNotNull(schema.professional().skillsTechnical());
        assertTrue(schema.professional().skillsTechnical().contains("React"));
    }

    @Test
    void normalizeRoleFromHeadline_bulletSeparator() {
        assertEquals("Data Scientist", OAuthProfileParser.normalizeRoleFromHeadline("Data Scientist • Company"));
    }

    @Test
    void normalizeRoleFromHeadline_atSymbol() {
        assertEquals("ML Engineer", OAuthProfileParser.normalizeRoleFromHeadline("ML Engineer @ StartupXYZ"));
    }

    @Test
    void extractKnownSkills_golang() {
        List<String> skills = OAuthProfileParser.extractKnownSkills("I write golang services");
        assertTrue(skills.contains("Go"));
    }

    @Test
    void extractKnownSkills_dotnet() {
        List<String> skills = OAuthProfileParser.extractKnownSkills(".NET developer using C# and Azure");
        assertTrue(skills.contains(".NET"));
        assertTrue(skills.contains("C#"));
        assertTrue(skills.contains("Azure"));
    }

    @Test
    void extractKnownSkills_cppAndRust() {
        List<String> skills = OAuthProfileParser.extractKnownSkills("c++ and rust systems programmer");
        assertTrue(skills.contains("C++"));
        assertTrue(skills.contains("Rust"));
    }

    @Test
    void extractKnownSkills_mongodbRedisKafka() {
        List<String> skills = OAuthProfileParser.extractKnownSkills("mongodb, redis, kafka specialist");
        assertTrue(skills.contains("MongoDB"));
        assertTrue(skills.contains("Redis"));
        assertTrue(skills.contains("Kafka"));
    }

    @Test
    void extractKnownSkills_mysqlGcp() {
        List<String> skills = OAuthProfileParser.extractKnownSkills("mysql and gcp cloud architect");
        assertTrue(skills.contains("MySQL"));
        assertTrue(skills.contains("GCP"));
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
