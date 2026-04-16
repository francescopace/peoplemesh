package org.peoplemesh.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.domain.model.MeshNode;
import org.peoplemesh.domain.model.UserIdentity;
import org.peoplemesh.service.OAuthTokenExchangeService.OidcSubject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuthCallbackServiceLoginTest {

    private AppConfig appConfig;
    private AppConfig.EntitlementsConfig entitlements;
    private ProfileService profileService;

    @BeforeEach
    void setUp() {
        appConfig = mock(AppConfig.class);
        entitlements = mock(AppConfig.EntitlementsConfig.class);
        profileService = mock(ProfileService.class);
        when(appConfig.entitlements()).thenReturn(entitlements);
        when(entitlements.isAdmin()).thenReturn(Optional.empty());
    }

    @Test
    void handleLogin_existingIdentity_existingNode_updatesLastActiveAndProfile() {
        TestableOAuthCallbackService service = baseService();
        UUID nodeId = UUID.randomUUID();
        UserIdentity identity = new UserIdentity();
        identity.nodeId = nodeId;
        identity.oauthProvider = "google";
        identity.oauthSubject = "sub1";
        service.identityByOauth = Optional.of(identity);
        service.nodeById = Optional.of(userNode(nodeId, null));

        OidcSubject subject = new OidcSubject("sub1", "Jane Doe", "Jane", "Doe",
                "jane@example.com", null, null, "en", "pic", null);

        OAuthCallbackService.LoginResult result = service.handleLogin("google", subject);

        assertFalse(result.isNewUser());
        assertEquals(nodeId, result.userId());
        assertEquals("Jane Doe", result.displayName());
        assertNotNull(identity.lastActiveAt);
        assertEquals("jane@example.com", service.lastPersistedNode.externalId);
        assertTrue(service.consentsRecorded.isEmpty());
        verify(profileService).upsertProfileFromProvider(
                eq(nodeId), eq("google"), eq("Jane Doe"),
                eq("Jane"), eq("Doe"), eq("jane@example.com"),
                eq("pic"), eq("en"), eq((String) null));
    }

    @Test
    void handleLogin_existingIdentity_missingNode_createsNodeAndRelinksIdentity() {
        TestableOAuthCallbackService service = baseService();
        UserIdentity identity = new UserIdentity();
        identity.nodeId = UUID.randomUUID();
        identity.oauthProvider = "google";
        identity.oauthSubject = "sub2";
        service.identityByOauth = Optional.of(identity);
        service.nodeById = Optional.empty();

        OidcSubject subject = new OidcSubject("sub2", "John", "John", null,
                "john@example.com", null, null, null, null, null);

        OAuthCallbackService.LoginResult result = service.handleLogin("google", subject);

        assertFalse(result.isNewUser());
        assertEquals(result.userId(), identity.nodeId);
        assertNotNull(service.createdUserNode);
        assertEquals("john@example.com", service.createdUserNode.externalId);
    }

    @Test
    void handleLogin_noIdentity_existingUserByEmail_createsIdentityWithoutNewUserConsents() {
        TestableOAuthCallbackService service = baseService();
        UUID nodeId = UUID.randomUUID();
        MeshNode existing = userNode(nodeId, "existing@example.com");
        service.identityByOauth = Optional.empty();
        service.nodeByExternalId = Optional.of(existing);

        OidcSubject subject = new OidcSubject("sub3", "Existing User", "Ex", "User",
                "existing@example.com", null, null, null, null, null);

        OAuthCallbackService.LoginResult result = service.handleLogin("google", subject);

        assertFalse(result.isNewUser());
        assertEquals(nodeId, result.userId());
        assertNotNull(service.lastPersistedIdentity);
        assertEquals(nodeId, service.lastPersistedIdentity.nodeId);
        assertTrue(service.consentsRecorded.isEmpty());
    }

    @Test
    void handleLogin_noIdentity_noUser_createsNewUserAndRecordsDefaultConsents() {
        TestableOAuthCallbackService service = baseService();
        service.identityByOauth = Optional.empty();
        service.nodeByExternalId = Optional.empty();

        OidcSubject subject = new OidcSubject("sub4", "New User", "New", "User",
                "new@example.com", null, null, null, null, null);

        OAuthCallbackService.LoginResult result = service.handleLogin("google", subject);

        assertTrue(result.isNewUser());
        assertNotNull(service.createdUserNode);
        assertEquals("new@example.com", service.createdUserNode.externalId);
        assertEquals(2, service.consentsRecorded.size());
        assertTrue(service.consentsRecorded.contains("embedding_processing"));
        assertTrue(service.consentsRecorded.contains("professional_matching"));
    }

    @Test
    void handleLogin_syncsEntitlementsFromConfigLists() {
        TestableOAuthCallbackService service = baseService();
        UUID nodeId = UUID.randomUUID();
        UserIdentity identity = new UserIdentity();
        identity.nodeId = nodeId;
        identity.oauthProvider = "google";
        identity.oauthSubject = "sub-ent";
        service.identityByOauth = Optional.of(identity);
        service.nodeById = Optional.of(userNode(nodeId, "ent@example.com"));
        when(entitlements.isAdmin()).thenReturn(Optional.of(List.of("sub-ent")));

        OidcSubject subject = new OidcSubject("sub-ent", "Ent User", "Ent", "User",
                "ent@example.com", null, null, null, null, null);

        service.handleLogin("google", subject);

        assertTrue(identity.isAdmin);
    }

    private TestableOAuthCallbackService baseService() {
        TestableOAuthCallbackService service = new TestableOAuthCallbackService();
        service.appConfig = appConfig;
        service.profileService = profileService;
        service.tokenExchangeService = mock(OAuthTokenExchangeService.class);
        service.consentService = mock(ConsentService.class);
        return service;
    }

    private static MeshNode userNode(UUID id, String externalId) {
        MeshNode node = new MeshNode();
        node.id = id;
        node.externalId = externalId;
        node.searchable = true;
        node.title = "Anonymous";
        node.description = "";
        node.tags = new ArrayList<>();
        node.structuredData = new LinkedHashMap<>();
        node.createdAt = Instant.now();
        node.updatedAt = Instant.now();
        return node;
    }

    private static final class TestableOAuthCallbackService extends OAuthCallbackService {
        Optional<UserIdentity> identityByOauth = Optional.empty();
        Optional<MeshNode> nodeById = Optional.empty();
        Optional<MeshNode> nodeByExternalId = Optional.empty();

        UserIdentity lastPersistedIdentity;
        MeshNode lastPersistedNode;
        MeshNode createdUserNode;
        final Set<String> consentsRecorded = new java.util.LinkedHashSet<>();

        @Override
        Optional<UserIdentity> findIdentityByOauth(String provider, String subject) {
            return identityByOauth;
        }

        @Override
        Optional<MeshNode> findNodeById(UUID nodeId) {
            return nodeById;
        }

        @Override
        Optional<MeshNode> findUserNodeByExternalId(String email) {
            return nodeByExternalId;
        }

        @Override
        void persistIdentity(UserIdentity identity) {
            lastPersistedIdentity = identity;
        }

        @Override
        void persistNode(MeshNode node) {
            if (node.id == null) {
                node.id = UUID.randomUUID();
            }
            if (node.createdAt == null) {
                node.createdAt = Instant.now();
            }
            node.updatedAt = Instant.now();
            if (node.title != null && "Anonymous".equals(node.title) && createdUserNode == null) {
                createdUserNode = node;
            }
            lastPersistedNode = node;
            if (nodeById.isEmpty()) {
                nodeById = Optional.of(node);
            }
        }

        @Override
        void recordConsent(UUID nodeId, String scope) {
            consentsRecorded.add(scope);
        }

        @Override
        Instant now() {
            return Instant.parse("2026-01-01T00:00:00Z");
        }
    }
}
