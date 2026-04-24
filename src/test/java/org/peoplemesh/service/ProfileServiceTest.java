package org.peoplemesh.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.domain.dto.ProfileSchema;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.model.MeshNode;
import org.peoplemesh.repository.NodeRepository;

import java.time.Instant;
import java.util.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {
    private static final String SOURCE_CV = "cv_docling_llm";
    private static final String SOURCE_GITHUB = "github";

    @Mock EmbeddingService embeddingService;
    @Mock AuditService audit;
    @Mock ConsentService consentService;
    @Mock NodeRepository nodeRepository;
    @Mock SkillsService skillsService;

    @InjectMocks
    ProfileService profileService;
    private ProfileSkillUsageService profileSkillUsageService;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        profileSkillUsageService = new ProfileSkillUsageService();
        profileSkillUsageService.skillsService = skillsService;
        profileService.profileSkillUsageService = profileSkillUsageService;
        lenient().when(skillsService.normalizeSkills(anyList())).thenAnswer(invocation -> {
            List<String> values = invocation.getArgument(0);
            return normalizeSet(values);
        });
        lenient().when(skillsService.canonicalizeSkillList(any(), anyBoolean())).thenAnswer(invocation -> {
            List<String> values = invocation.getArgument(0);
            return new ArrayList<>(normalizeSet(values));
        });
    }

    @Test
    void getProfile_noNode_returnsEmpty() {
        when(nodeRepository.findPublishedUserNode(userId)).thenReturn(Optional.empty());
        assertTrue(profileService.getProfile(userId).isEmpty());
    }

    @Test
    void getProfile_withNode_returnsSchema() {
        MeshNode node = createMockNode(userId);
        node.structuredData = new LinkedHashMap<>();
        when(nodeRepository.findPublishedUserNode(userId)).thenReturn(Optional.of(node));

        Optional<ProfileSchema> result = profileService.getProfile(userId);

        assertTrue(result.isPresent());
    }

    @Test
    void getPublicProfile_userNode_returnsSchema() {
        MeshNode node = createMockNode(userId);
        when(nodeRepository.findById(userId)).thenReturn(Optional.of(node));

        Optional<ProfileSchema> result = profileService.getPublicProfile(userId);

        assertTrue(result.isPresent());
    }

    @Test
    void getPublicProfile_nonUserNode_returnsEmpty() {
        MeshNode node = createMockNode(userId);
        node.nodeType = NodeType.PROJECT;
        when(nodeRepository.findById(userId)).thenReturn(Optional.of(node));

        Optional<ProfileSchema> result = profileService.getPublicProfile(userId);

        assertTrue(result.isEmpty());
    }

    @Test
    void upsertProfile_createsOrUpdatesNode() {
        when(consentService.hasActiveConsent(eq(userId), anyString())).thenReturn(false);
        MeshNode node = createMockNode(userId);
        when(nodeRepository.findPublishedUserNode(userId)).thenReturn(Optional.of(node));

        MeshNode result = profileService.upsertProfile(userId, minimalSchema());

        assertNotNull(result);
        verify(nodeRepository).persist(node);
        verify(audit).log(userId, "PROFILE_UPDATED", "peoplemesh_upsert_profile");
    }

    @Test
    void upsertProfileFromProvider_existingNode_avatarOnlySetIfNull() {
        MeshNode node = createMockNode(userId);
        node.structuredData = new LinkedHashMap<>();
        node.structuredData.put("avatar_url", "existing-url");
        when(nodeRepository.findById(userId)).thenReturn(Optional.of(node));
        when(consentService.hasActiveConsent(eq(userId), anyString())).thenReturn(false);

        profileService.upsertProfileFromProvider(
                userId, "google", "Test", null, null,
                null, "http://new-photo.url", null, null);

        assertEquals("existing-url", node.structuredData.get("avatar_url"));
    }

    @Test
    void upsertProfileFromProvider_existingNode_setsDisplayName() {
        MeshNode node = createMockNode(userId);
        when(nodeRepository.findById(userId)).thenReturn(Optional.of(node));
        when(consentService.hasActiveConsent(eq(userId), anyString())).thenReturn(false);

        profileService.upsertProfileFromProvider(
                userId, "github", "Jane Smith", "Jane", "Smith",
                "jane@test.com", null, null, null);

        assertEquals("Jane Smith", node.title);
        assertEquals("jane@test.com", node.structuredData.get("email"));
    }

    @Test
    void upsertProfileFromProvider_setsIdentityBirthDateWhenMissing() {
        MeshNode node = createMockNode(userId);
        when(nodeRepository.findById(userId)).thenReturn(Optional.of(node));
        when(consentService.hasActiveConsent(eq(userId), anyString())).thenReturn(false);

        profileService.upsertProfileFromProvider(
                userId, "google", "Jane Smith", "Jane", "Smith",
                "jane@test.com", null, "1988-05-17", null);

        assertEquals("1988-05-17", node.structuredData.get("birth_date"));
        @SuppressWarnings("unchecked")
        Map<String, String> provenance = (Map<String, String>) node.structuredData.get("field_provenance");
        assertEquals("google", provenance.get("identity.birth_date"));
    }

    @Test
    void upsertProfileFromProvider_joinsNamesAndKeepsExistingExternalId() {
        MeshNode node = createMockNode(userId);
        node.externalId = "already@set.com";
        when(nodeRepository.findById(userId)).thenReturn(Optional.of(node));
        when(consentService.hasActiveConsent(eq(userId), anyString())).thenReturn(false);

        profileService.upsertProfileFromProvider(
                userId, "google", null, "Jane", "Doe",
                "new@example.com", null, null, null);

        assertEquals("Jane Doe", node.title);
        assertEquals("already@set.com", node.externalId);
        assertEquals("new@example.com", node.structuredData.get("email"));
    }

    @Test
    void applySelectiveImport_blocksIdentityOverwriteExceptBirthDate() {
        MeshNode node = createMockNode(userId);
        node.title = "Google Name";
        Map<String, String> provenance = new HashMap<>();
        provenance.put("identity.display_name", "google");
        node.structuredData.put("field_provenance", provenance);

        ProfileSchema selected = new ProfileSchema(
                null, null, null,
                new ProfileSchema.ProfessionalInfo(
                        List.of("Engineer", "Architect"), null, null,
                        List.of("Java"), null, null, null, null, null),
                null,
                null, null, null, null,
                new ProfileSchema.IdentityInfo("Should Not Override", "First", "Last",
                        "id@example.com", null, null, "1990-01-01")
        );

        when(consentService.hasActiveConsent(eq(userId), anyString())).thenReturn(false);
        when(nodeRepository.findPublishedUserNode(userId)).thenReturn(Optional.of(node));

        profileService.applySelectiveImport(userId, selected, "github");

        assertEquals("Google Name", node.title);
        assertNull(node.structuredData.get("first_name"));
        assertNull(node.structuredData.get("email"));
        assertEquals("1990-01-01", node.structuredData.get("birth_date"));
        assertEquals("Engineer", node.description);
        verify(audit).log(userId, "PROFILE_SELECTIVE_IMPORT", "peoplemesh_selective_import_github");
    }

    @Test
    void applySelectiveImport_updatesBirthDateAndEmbeddingWhenConsented() {
        MeshNode node = createMockNode(userId);
        ProfileSchema selected = new ProfileSchema(
                null, null, null,
                null, null, null,
                null,
                new ProfileSchema.GeographyInfo("DE", "Berlin", "Europe/Berlin"),
                null,
                new ProfileSchema.IdentityInfo("Jane D", "Jane", "Doe",
                        "jane@example.com", "https://img", "ACME", "1988-05-17")
        );

        when(consentService.hasActiveConsent(eq(userId), anyString())).thenReturn(true);
        when(embeddingService.generateEmbedding(anyString())).thenReturn(new float[]{0.1f, 0.2f});
        when(nodeRepository.findPublishedUserNode(userId)).thenReturn(Optional.of(node));

        profileService.applySelectiveImport(userId, selected, "manual");

        assertEquals("Berlin", node.structuredData.get("city"));
        assertEquals("1988-05-17", node.structuredData.get("birth_date"));
        assertEquals("DE", node.country);
        assertTrue(node.searchable);
        assertNotNull(node.embedding);
    }

    @Test
    void upsertProfile_updatesOnlyIdentityBirthDate() {
        MeshNode node = createMockNode(userId);
        node.title = "OAuth Name";
        node.structuredData.put("first_name", "OAuthFirst");
        node.structuredData.put("last_name", "OAuthLast");
        node.structuredData.put("email", "oauth@example.com");
        node.structuredData.put("avatar_url", "https://oauth-photo");
        node.structuredData.put("company", "OAuth Co");
        when(nodeRepository.findPublishedUserNode(userId)).thenReturn(Optional.of(node));
        when(consentService.hasActiveConsent(eq(userId), anyString())).thenReturn(false);

        ProfileSchema updates = new ProfileSchema(
                null, null, null,
                null, null, null, null, null, null,
                new ProfileSchema.IdentityInfo(
                        "Manual Name",
                        "ManualFirst",
                        "ManualLast",
                        "manual@example.com",
                        "https://manual-photo",
                        "Manual Co",
                        "1992-04-12"
                )
        );

        profileService.upsertProfile(userId, updates);

        assertEquals("OAuth Name", node.title);
        assertEquals("OAuthFirst", node.structuredData.get("first_name"));
        assertEquals("OAuthLast", node.structuredData.get("last_name"));
        assertEquals("oauth@example.com", node.structuredData.get("email"));
        assertEquals("https://oauth-photo", node.structuredData.get("avatar_url"));
        assertEquals("OAuth Co", node.structuredData.get("company"));
        assertEquals("1992-04-12", node.structuredData.get("birth_date"));
    }

    @Test
    void upsertProfile_whenEmbeddingConsentGrantedButModelReturnsNull_marksNotSearchable() {
        MeshNode node = createMockNode(userId);
        when(nodeRepository.findPublishedUserNode(userId)).thenReturn(Optional.of(node));
        when(consentService.hasActiveConsent(eq(userId), anyString())).thenReturn(true);
        when(embeddingService.generateEmbedding(anyString())).thenReturn(null);

        profileService.upsertProfile(userId, minimalSchema());

        assertNull(node.embedding);
        assertFalse(node.searchable);
    }

    @Test
    void applySelectiveImport_updatesContactFieldsFromContactsSection() {
        MeshNode node = createMockNode(userId);
        ProfileSchema selected = new ProfileSchema(
                null, null, null,
                null,
                new ProfileSchema.ContactsInfo(
                        "@jane",
                        "@jane_tg",
                        "+39123456789",
                        "https://www.linkedin.com/in/jane-doe"
                ),
                null, null, null, null, null
        );

        when(consentService.hasActiveConsent(eq(userId), anyString())).thenReturn(false);
        when(nodeRepository.findPublishedUserNode(userId)).thenReturn(Optional.of(node));

        profileService.applySelectiveImport(userId, selected, "github");

        assertEquals("@jane", node.structuredData.get("slack_handle"));
        assertEquals("@jane_tg", node.structuredData.get("telegram_handle"));
        assertEquals("+39123456789", node.structuredData.get("mobile_phone"));
        assertEquals("https://www.linkedin.com/in/jane-doe", node.structuredData.get("linkedin_url"));
        @SuppressWarnings("unchecked")
        Map<String, String> provenance = (Map<String, String>) node.structuredData.get("field_provenance");
        assertEquals("github", provenance.get("contacts.slack_handle"));
        assertEquals("github", provenance.get("contacts.telegram_handle"));
        assertEquals("github", provenance.get("contacts.mobile_phone"));
        assertEquals("github", provenance.get("contacts.linkedin_url"));
    }

    @Test
    void applySelectiveImport_updatesPersonalFields() {
        MeshNode node = createMockNode(userId);
        ProfileSchema selected = new ProfileSchema(
                null, null, null,
                null, null,
                null,
                new ProfileSchema.PersonalInfo(
                        List.of("hiking"),
                        List.of("running"),
                        List.of("BSc Computer Science"),
                        List.of("open source"),
                        List.of("curious"),
                        List.of("jazz"),
                        List.of("fiction")
                ),
                null, null, null
        );

        when(consentService.hasActiveConsent(eq(userId), anyString())).thenReturn(false);
        when(nodeRepository.findPublishedUserNode(userId)).thenReturn(Optional.of(node));

        profileService.applySelectiveImport(userId, selected, "cv_docling_llm");

        assertEquals(List.of("hiking"), node.structuredData.get("hobbies"));
        assertEquals(List.of("running"), node.structuredData.get("sports"));
        assertEquals(List.of("BSc Computer Science"), node.structuredData.get("education"));
        assertEquals(List.of("open source"), node.structuredData.get("causes"));
        assertEquals(List.of("curious"), node.structuredData.get("personality_tags"));
        assertEquals(List.of("jazz"), node.structuredData.get("music_genres"));
        assertEquals(List.of("fiction"), node.structuredData.get("book_genres"));
    }

    @Test
    void toSchema_convertsNodeToProfileSchema() {
        MeshNode node = createMockNode(userId);
        node.structuredData = new LinkedHashMap<>();
        node.structuredData.put("seniority", "SENIOR");
        node.structuredData.put("email", "test@test.com");
        node.structuredData.put("slack_handle", "@jane");
        node.structuredData.put("telegram_handle", "@jane_tg");
        node.structuredData.put("mobile_phone", "+39123456789");
        node.structuredData.put("linkedin_url", "https://www.linkedin.com/in/jane");
        node.structuredData.put("birth_date", "1988-05-17");
        node.description = "Developer";
        node.tags = List.of("Java");
        node.country = "IT";
        node.createdAt = Instant.now();

        ProfileSchema schema = profileService.toSchema(node);

        assertNotNull(schema);
        assertNotNull(schema.professional());
        assertNotNull(schema.contacts());
        assertNotNull(schema.geography());
        assertNotNull(schema.identity());
        assertEquals("IT", schema.geography().country());
        assertEquals("@jane", schema.contacts().slackHandle());
        assertEquals("@jane_tg", schema.contacts().telegramHandle());
        assertEquals("+39123456789", schema.contacts().mobilePhone());
        assertEquals("https://www.linkedin.com/in/jane", schema.contacts().linkedinUrl());
        assertEquals("1988-05-17", schema.identity().birthDate());
    }

    @Test
    void toSchema_withInvalidEnumValues_throws() {
        MeshNode node = createMockNode(userId);
        node.structuredData = new LinkedHashMap<>();
        node.structuredData.put("seniority", "INVALID");
        node.structuredData.put("work_mode", "BAD_MODE");
        node.structuredData.put("employment_type", "BAD_EMPLOYMENT");

        assertThrows(IllegalArgumentException.class, () -> profileService.toSchema(node));
    }

    @Test
    void privateHelperMethods_coverNameAndListFallbackBranches() throws Exception {
        Method firstNonBlank = ProfileService.class.getDeclaredMethod("firstNonBlankListValue", List.class);
        firstNonBlank.setAccessible(true);
        Method joinName = ProfileService.class.getDeclaredMethod("joinName", String.class, String.class);
        joinName.setAccessible(true);

        assertNull(firstNonBlank.invoke(null, new Object[]{null}));
        assertNull(firstNonBlank.invoke(null, List.of()));
        assertEquals("Jane", firstNonBlank.invoke(null, List.of("   ", "Jane")));

        assertEquals("Jane Doe", joinName.invoke(null, "Jane", "Doe"));
        assertEquals("Jane", joinName.invoke(null, "Jane", null));
        assertEquals("Doe", joinName.invoke(null, null, "Doe"));
    }

    @Test
    void helperMethods_coverHasTextHasValueAndParseEnumBranches() throws Exception {
        Method hasText = ProfileService.class.getDeclaredMethod("hasText", String.class);
        hasText.setAccessible(true);
        Method hasValue = ProfileService.class.getDeclaredMethod("hasValue", List.class);
        hasValue.setAccessible(true);
        Method parseEnum = ProfileService.class.getDeclaredMethod("parseEnum", Class.class, String.class);
        parseEnum.setAccessible(true);

        assertEquals(false, hasText.invoke(null, (Object) null));
        assertEquals(false, hasText.invoke(null, "   "));
        assertEquals(true, hasText.invoke(null, "x"));

        assertEquals(false, hasValue.invoke(null, (Object) null));
        assertEquals(false, hasValue.invoke(null, List.of()));
        assertEquals(true, hasValue.invoke(null, List.of("x")));

        assertEquals(null, parseEnum.invoke(null, org.peoplemesh.domain.enums.WorkMode.class, null));
        assertEquals(null, parseEnum.invoke(null, org.peoplemesh.domain.enums.WorkMode.class, " "));
        InvocationTargetException ex = assertThrows(
                InvocationTargetException.class,
                () -> parseEnum.invoke(null, org.peoplemesh.domain.enums.WorkMode.class, "NOPE")
        );
        assertTrue(ex.getCause() instanceof IllegalArgumentException);
        assertEquals(org.peoplemesh.domain.enums.WorkMode.REMOTE,
                parseEnum.invoke(null, org.peoplemesh.domain.enums.WorkMode.class, "REMOTE"));
    }

    @Test
    void applySelectiveImport_withBlankAndEmptyFields_keepsExistingStructuredValues() {
        MeshNode node = createMockNode(userId);
        node.country = "IT";
        node.structuredData.put("city", "Milan");
        node.structuredData.put("timezone", "Europe/Rome");
        node.structuredData.put("slack_handle", "@existing");
        node.structuredData.put("project_types", List.of("backend"));
        node.tags = List.of("java");

        ProfileSchema selected = new ProfileSchema(
                null, null, null,
                new ProfileSchema.ProfessionalInfo(
                        List.of("   "),
                        null,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        null,
                        null
                ),
                new ProfileSchema.ContactsInfo("   ", "", "   ", "   "),
                new ProfileSchema.InterestsInfo(List.of(), List.of()),
                new ProfileSchema.PersonalInfo(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()),
                new ProfileSchema.GeographyInfo(" ", " ", " "),
                null,
                null
        );

        when(consentService.hasActiveConsent(eq(userId), anyString())).thenReturn(false);
        when(nodeRepository.findPublishedUserNode(userId)).thenReturn(Optional.of(node));

        profileService.applySelectiveImport(userId, selected, "manual");

        assertEquals("IT", node.country);
        assertEquals("Milan", node.structuredData.get("city"));
        assertEquals("Europe/Rome", node.structuredData.get("timezone"));
        assertEquals("@existing", node.structuredData.get("slack_handle"));
        assertEquals(List.of("java"), node.tags);
    }

    @Test
    void upsertProfileFromProvider_blankPhotoBirthDateCompany_doNotOverwrite() {
        MeshNode node = createMockNode(userId);
        node.structuredData.put("avatar_url", "https://existing/avatar");
        node.structuredData.put("birth_date", "1990-01-01");
        node.structuredData.put("company", "Existing Co");
        when(nodeRepository.findById(userId)).thenReturn(Optional.of(node));
        when(consentService.hasActiveConsent(eq(userId), anyString())).thenReturn(false);

        profileService.upsertProfileFromProvider(
                userId,
                "google",
                null,
                null,
                null,
                "new@email.com",
                "   ",
                " ",
                ""
        );

        assertEquals("https://existing/avatar", node.structuredData.get("avatar_url"));
        assertEquals("1990-01-01", node.structuredData.get("birth_date"));
        assertEquals("Existing Co", node.structuredData.get("company"));
        assertEquals("new@email.com", node.structuredData.get("email"));
    }

    @Test
    void upsertProfileFromProvider_nonUserNode_fallsBackToCreateUserNode() {
        MeshNode existingProject = createMockNode(userId);
        existingProject.nodeType = NodeType.PROJECT;

        when(nodeRepository.findById(userId)).thenReturn(Optional.of(existingProject));
        when(nodeRepository.findPublishedUserNode(userId)).thenReturn(Optional.empty());
        when(consentService.hasActiveConsent(eq(userId), anyString())).thenReturn(false);

        MeshNode result = profileService.upsertProfileFromProvider(
                userId,
                "github",
                "Jane Doe",
                "Jane",
                "Doe",
                "jane@example.com",
                "https://img.example/avatar.jpg",
                "1993-09-07",
                "PeopleMesh"
        );

        assertEquals(NodeType.USER, result.nodeType);
        assertEquals("Jane Doe", result.title);
        assertEquals("jane@example.com", result.structuredData.get("email"));
        assertEquals("https://img.example/avatar.jpg", result.structuredData.get("avatar_url"));
        assertEquals("1993-09-07", result.structuredData.get("birth_date"));
        assertEquals("PeopleMesh", result.structuredData.get("company"));
        verify(nodeRepository, atLeast(2)).persist(any(MeshNode.class));
    }

    @Test
    void applySelectiveImport_updatesProfessionalAndInterestFamiliesWhenPresent() {
        MeshNode node = createMockNode(userId);
        node.structuredData.put("field_provenance", new HashMap<String, String>());

        ProfileSchema selected = new ProfileSchema(
                null,
                null,
                null,
                new ProfileSchema.ProfessionalInfo(
                        List.of("Staff Engineer"),
                        org.peoplemesh.domain.enums.Seniority.LEAD,
                        List.of("Software", "SaaS"),
                        List.of("Java", "Quarkus"),
                        List.of("Mentoring"),
                        List.of("Kubernetes"),
                        List.of("English (professional working proficiency)", "it"),
                        org.peoplemesh.domain.enums.WorkMode.HYBRID,
                        org.peoplemesh.domain.enums.EmploymentType.OPEN_TO_OFFERS
                ),
                null,
                new ProfileSchema.InterestsInfo(
                        List.of("Graphs"),
                        List.of("Platform")
                ),
                null,
                null,
                null,
                null
        );

        when(consentService.hasActiveConsent(eq(userId), anyString())).thenReturn(false);
        when(nodeRepository.findPublishedUserNode(userId)).thenReturn(Optional.of(node));

        profileService.applySelectiveImport(userId, selected, "manual");

        assertEquals("Staff Engineer", node.description);
        assertEquals("LEAD", node.structuredData.get("seniority"));
        assertEquals("Software,SaaS", node.structuredData.get("industries"));
        assertEquals(List.of("java", "quarkus"), node.tags);
        assertEquals(List.of("mentoring"), node.structuredData.get("skills_soft"));
        assertEquals(List.of("kubernetes"), node.structuredData.get("tools_and_tech"));
        assertEquals(List.of("English", "Italian"), node.structuredData.get("languages_spoken"));
        assertEquals("HYBRID", node.structuredData.get("work_mode"));
        assertEquals("OPEN_TO_OFFERS", node.structuredData.get("employment_type"));
        assertEquals(List.of("Graphs"), node.structuredData.get("learning_areas"));
        assertEquals(List.of("Platform"), node.structuredData.get("project_types"));
    }

    @Test
    void upsertProfile_applySchemaToNode_populatesAllSchemaFamilies() {
        MeshNode node = createMockNode(userId);
        node.structuredData.put("field_provenance", Map.of("identity.birth_date", "manual"));
        when(nodeRepository.findPublishedUserNode(userId)).thenReturn(Optional.of(node));
        when(consentService.hasActiveConsent(eq(userId), anyString())).thenReturn(false);

        ProfileSchema schema = new ProfileSchema(
                "v2",
                null,
                null,
                new ProfileSchema.ProfessionalInfo(
                        List.of("Principal Engineer"),
                        org.peoplemesh.domain.enums.Seniority.SENIOR,
                        List.of("Software"),
                        List.of("Java"),
                        List.of("Communication"),
                        List.of("Docker"),
                        List.of("English"),
                        org.peoplemesh.domain.enums.WorkMode.REMOTE,
                        org.peoplemesh.domain.enums.EmploymentType.EMPLOYED
                ),
                new ProfileSchema.ContactsInfo("@alice", "@alice_tg", "+39000", "linkedin.com/in/alice"),
                new ProfileSchema.InterestsInfo(List.of("LLM"), List.of("Platform")),
                new ProfileSchema.PersonalInfo(List.of("hiking"), List.of("cycling"), List.of("MSc"), List.of("OSS"),
                        List.of("curious"), List.of("jazz"), List.of("sci-fi")),
                new ProfileSchema.GeographyInfo("IT", "Rome", "Europe/Rome"),
                Map.of("professional.roles", "github"),
                new ProfileSchema.IdentityInfo("Ignored", "Ignored", "Ignored",
                        "ignored@example.com", "https://ignored", "Ignored Co", "2000-01-02")
        );

        profileService.upsertProfile(userId, schema);

        assertEquals("v2", node.structuredData.get("profile_version"));
        assertEquals("Principal Engineer", node.description);
        assertEquals("SENIOR", node.structuredData.get("seniority"));
        assertEquals("Software", node.structuredData.get("industries"));
        assertEquals(List.of("java"), node.tags);
        assertEquals("@alice", node.structuredData.get("slack_handle"));
        assertEquals("Rome", node.structuredData.get("city"));
        assertEquals("Europe/Rome", node.structuredData.get("timezone"));
        assertEquals("2000-01-02", node.structuredData.get("birth_date"));
        @SuppressWarnings("unchecked")
        Map<String, String> provenance = (Map<String, String>) node.structuredData.get("field_provenance");
        assertEquals("github", provenance.get("professional.roles"));
        assertEquals("manual", provenance.get("identity.birth_date"));
    }

    @Test
    void applySelectiveImport_prefersCvForNarrativeFieldsAndMergesGithubTechnicalSignals() {
        MeshNode node = createMockNode(userId);
        node.description = "Developer Advocate";
        node.tags = new ArrayList<>(List.of("Quarkus", "GitHub Actions"));
        node.country = "US";
        node.structuredData.put("industries", "Developer Relations");
        node.structuredData.put("tools_and_tech", List.of("GitHub"));
        node.structuredData.put("languages_spoken", List.of("English"));
        node.structuredData.put("learning_areas", List.of("Semantic Search"));
        node.structuredData.put("project_types", List.of("Platform Engineering"));
        node.structuredData.put("field_provenance", new HashMap<>(Map.of(
                "professional.roles", "github",
                "professional.industries", "github",
                "professional.skills_technical", "github",
                "professional.tools_and_tech", "github",
                "professional.languages_spoken", "github",
                "interests_professional.learning_areas", "github",
                "interests_professional.project_types", "github",
                "geography.country", "github"
        )));

        ProfileSchema selected = new ProfileSchema(
                null,
                null,
                null,
                new ProfileSchema.ProfessionalInfo(
                        List.of("Commercial Account Executive"),
                        org.peoplemesh.domain.enums.Seniority.SENIOR,
                        List.of("IT Services", "Cloud Computing"),
                        List.of("Java", "Quarkus"),
                        null,
                        List.of("Kafka"),
                        List.of("Italian", "English"),
                        null,
                        null
                ),
                null,
                new ProfileSchema.InterestsInfo(
                        List.of("DevOps"),
                        List.of("Developer Portals")
                ),
                null,
                new ProfileSchema.GeographyInfo("IT", "Rome", null),
                null,
                null
        );

        when(consentService.hasActiveConsent(eq(userId), anyString())).thenReturn(false);
        when(nodeRepository.findPublishedUserNode(userId)).thenReturn(Optional.of(node));

        profileService.applySelectiveImport(userId, selected, SOURCE_CV);

        assertEquals("Commercial Account Executive", node.description);
        assertEquals("IT Services,Cloud Computing,Developer Relations", node.structuredData.get("industries"));
        assertEquals(List.of("java", "quarkus", "github actions"), node.tags);
        assertEquals(List.of("kafka", "github"), node.structuredData.get("tools_and_tech"));
        assertEquals(List.of("Italian", "English"), node.structuredData.get("languages_spoken"));
        assertEquals(List.of("DevOps", "Semantic Search"), node.structuredData.get("learning_areas"));
        assertEquals(List.of("Developer Portals", "Platform Engineering"), node.structuredData.get("project_types"));
        assertEquals("IT", node.country);
        assertEquals("Rome", node.structuredData.get("city"));
    }

    @Test
    void applySelectiveImport_githubDoesNotOverrideCvNarrativeFields() {
        MeshNode node = createMockNode(userId);
        node.description = "Commercial Account Executive";
        node.country = "IT";
        node.tags = new ArrayList<>(List.of("Java"));
        node.structuredData.put("industries", "IT Services");
        node.structuredData.put("field_provenance", new HashMap<>(Map.of(
                "professional.roles", SOURCE_CV,
                "professional.industries", SOURCE_CV,
                "professional.skills_technical", SOURCE_CV,
                "geography.country", SOURCE_CV
        )));

        ProfileSchema selected = new ProfileSchema(
                null,
                null,
                null,
                new ProfileSchema.ProfessionalInfo(
                        List.of("Developer Advocate"),
                        org.peoplemesh.domain.enums.Seniority.LEAD,
                        List.of("Developer Relations"),
                        List.of("Quarkus", "GitHub Actions"),
                        null,
                        null,
                        null,
                        null,
                        null
                ),
                null,
                null,
                null,
                new ProfileSchema.GeographyInfo("US", null, null),
                null,
                null
        );

        when(consentService.hasActiveConsent(eq(userId), anyString())).thenReturn(false);
        when(nodeRepository.findPublishedUserNode(userId)).thenReturn(Optional.of(node));

        profileService.applySelectiveImport(userId, selected, SOURCE_GITHUB);

        assertEquals("Commercial Account Executive", node.description);
        assertEquals("IT Services", node.structuredData.get("industries"));
        assertEquals("IT", node.country);
        assertEquals(List.of("java", "quarkus", "github actions"), node.tags);
        @SuppressWarnings("unchecked")
        Map<String, String> provenance = (Map<String, String>) node.structuredData.get("field_provenance");
        assertEquals(SOURCE_CV, provenance.get("professional.roles"));
        assertEquals(SOURCE_GITHUB, provenance.get("professional.skills_technical"));
    }

    // === Helpers ===

    private static MeshNode createMockNode(UUID nodeId) {
        MeshNode node = spy(new MeshNode());
        node.id = nodeId;
        node.nodeType = NodeType.USER;
        node.title = "Test User";
        node.description = "";
        node.tags = new ArrayList<>();
        node.structuredData = new LinkedHashMap<>();
        node.searchable = true;
        return node;
    }

    private static ProfileSchema minimalSchema() {
        return new ProfileSchema(null, null, null,
                new ProfileSchema.ProfessionalInfo(
                        List.of("Developer"), null, null,
                        List.of("Java"), null, null, null, null, null),
                null,
                null, null, new ProfileSchema.GeographyInfo("IT", null, null),
                null, null);
    }

    private static Set<String> normalizeSet(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String canonical = value.trim().toLowerCase(Locale.ROOT);
            if (!canonical.isBlank()) {
                normalized.add(canonical.replaceAll("\\s+", " "));
            }
        }
        return normalized;
    }
}
