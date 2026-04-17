package org.peoplemesh.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.domain.dto.ProfileSchema;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.model.MeshNode;
import org.peoplemesh.domain.model.SkillAssessment;
import org.peoplemesh.repository.NodeRepository;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock EmbeddingService embeddingService;
    @Mock AuditService audit;
    @Mock ConsentService consentService;
    @Mock NodeRepository nodeRepository;

    @InjectMocks
    ProfileService profileService;

    private final UUID userId = UUID.randomUUID();

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
    void applySelectiveImport_googleIdentityProvenance_blocksIdentityOverwrite() {
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
                        "id@example.com", null, null, null)
        );

        when(consentService.hasActiveConsent(eq(userId), anyString())).thenReturn(false);
        when(nodeRepository.findPublishedUserNode(userId)).thenReturn(Optional.of(node));

        profileService.applySelectiveImport(userId, selected, "github");

        assertEquals("Google Name", node.title);
        assertNull(node.structuredData.get("first_name"));
        assertEquals("Engineer", node.description);
        verify(audit).log(userId, "PROFILE_SELECTIVE_IMPORT", "peoplemesh_selective_import_github");
    }

    @Test
    void applySelectiveImport_updatesIdentityAndEmbeddingWhenConsented() {
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
        try (var skillsMock = mockStatic(SkillAssessment.class)) {
            skillsMock.when(() -> SkillAssessment.findByNode(userId)).thenReturn(List.of());

            profileService.applySelectiveImport(userId, selected, "manual");

            assertEquals("Jane D", node.title);
            assertEquals("jane@example.com", node.structuredData.get("email"));
            assertEquals("Berlin", node.structuredData.get("city"));
            assertEquals("1988-05-17", node.structuredData.get("birth_date"));
            assertEquals("ACME", node.structuredData.get("company"));
            assertEquals("DE", node.country);
            assertTrue(node.searchable);
            assertNotNull(node.embedding);
        }
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

    // === Helpers ===

    private static MeshNode createMockNode(UUID nodeId) {
        MeshNode node = spy(new MeshNode());
        node.id = nodeId;
        node.createdBy = nodeId;
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
}
