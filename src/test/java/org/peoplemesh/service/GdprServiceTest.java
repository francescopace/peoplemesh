package org.peoplemesh.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.domain.dto.ProfileSchema;
import org.peoplemesh.domain.dto.PrivacyDashboard;
import org.peoplemesh.domain.model.AuditLogEntry;
import org.peoplemesh.domain.model.MeshNode;
import org.peoplemesh.domain.model.MeshNodeConsent;
import org.peoplemesh.domain.model.UserIdentity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GdprServiceTest {

    @Mock AuditService audit;
    @Mock ProfileService profileService;
    @Mock ObjectMapper objectMapper;
    @Mock EntityManager em;

    @InjectMocks
    GdprService gdprService;

    private final UUID userId = UUID.randomUUID();

    @Test
    void deleteAllData_hardDeletesAllUserData() {
        @SuppressWarnings("unchecked")
        TypedQuery<Object> deleteQuery = mock(TypedQuery.class);
        when(em.createQuery(anyString())).thenReturn(deleteQuery);
        when(deleteQuery.setParameter(anyString(), any())).thenReturn(deleteQuery);
        when(deleteQuery.executeUpdate()).thenReturn(0);

        gdprService.deleteAllData(userId);

        verify(audit).log(userId, "ACCOUNT_DELETED", "gdpr_delete");
        verify(em, atLeast(3)).createQuery(anyString());
    }

    @Test
    void getPrivacyDashboard_noNode_returnsDefaults() {
        try (var meshMock = mockStatic(MeshNode.class);
             var consentMock = mockStatic(MeshNodeConsent.class)) {

            meshMock.when(() -> MeshNode.findPublishedUserNode(userId)).thenReturn(Optional.empty());
            consentMock.when(() -> MeshNodeConsent.findActiveByNodeId(userId)).thenReturn(Collections.emptyList());

            PrivacyDashboard dashboard = gdprService.getPrivacyDashboard(userId);

            assertNotNull(dashboard);
            assertNull(dashboard.lastProfileUpdate());
            assertFalse(dashboard.searchable());
            assertEquals(0, dashboard.activeConsents());
            assertTrue(dashboard.consentScopes().isEmpty());
        }
    }

    @Test
    void getPrivacyDashboard_withNode_returnsData() {
        try (var meshMock = mockStatic(MeshNode.class);
             var consentMock = mockStatic(MeshNodeConsent.class)) {

            MeshNode node = new MeshNode();
            node.updatedAt = Instant.now();
            node.searchable = true;
            meshMock.when(() -> MeshNode.findPublishedUserNode(userId)).thenReturn(Optional.of(node));

            MeshNodeConsent consent = new MeshNodeConsent();
            consent.scope = "professional_matching";
            consent.grantedAt = Instant.now();
            consent.policyVersion = "1.0";
            consentMock.when(() -> MeshNodeConsent.findActiveByNodeId(userId)).thenReturn(List.of(consent));

            PrivacyDashboard dashboard = gdprService.getPrivacyDashboard(userId);

            assertNotNull(dashboard.lastProfileUpdate());
            assertTrue(dashboard.searchable());
            assertEquals(1, dashboard.activeConsents());
            assertEquals(List.of("professional_matching"), dashboard.consentScopes());
        }
    }

    @Test
    void getPrivacyDashboard_distinctScopes() {
        try (var meshMock = mockStatic(MeshNode.class);
             var consentMock = mockStatic(MeshNodeConsent.class)) {

            meshMock.when(() -> MeshNode.findPublishedUserNode(userId)).thenReturn(Optional.empty());

            MeshNodeConsent c1 = new MeshNodeConsent(); c1.scope = "professional_matching"; c1.grantedAt = Instant.now(); c1.policyVersion = "1.0";
            MeshNodeConsent c2 = new MeshNodeConsent(); c2.scope = "embedding_processing"; c2.grantedAt = Instant.now(); c2.policyVersion = "1.0";
            consentMock.when(() -> MeshNodeConsent.findActiveByNodeId(userId)).thenReturn(List.of(c1, c2));

            PrivacyDashboard dashboard = gdprService.getPrivacyDashboard(userId);

            assertEquals(2, dashboard.activeConsents());
            assertEquals(2, dashboard.consentScopes().size());
        }
    }

    @Test
    void enforceRetention_deletesInactiveUsers() {
        Query nativeQuery = mock(Query.class);
        when(em.createNativeQuery(anyString(), eq(UUID.class))).thenReturn(nativeQuery);
        when(nativeQuery.setParameter(anyString(), any())).thenReturn(nativeQuery);
        UUID inactiveUser = UUID.randomUUID();
        when(nativeQuery.getResultList()).thenReturn(List.of(inactiveUser));

        @SuppressWarnings("unchecked")
        TypedQuery<Object> deleteQuery = mock(TypedQuery.class);
        when(em.createQuery(anyString())).thenReturn(deleteQuery);
        when(deleteQuery.setParameter(anyString(), any())).thenReturn(deleteQuery);
        when(deleteQuery.executeUpdate()).thenReturn(0);

        int count = gdprService.enforceRetention(12);

        assertEquals(1, count);
        verify(audit).log(inactiveUser, "ACCOUNT_DELETED", "gdpr_delete");
    }

    @Test
    void exportAllData_includesAllSectionsAndAudits() {
        TestableGdprService service = new TestableGdprService();
        service.audit = audit;
        service.profileService = profileService;
        service.objectMapper = new ObjectMapper();

        MeshNode userNode = new MeshNode();
        userNode.id = userId;
        userNode.externalId = "user@example.com";
        userNode.createdAt = Instant.parse("2026-01-01T00:00:00Z");
        userNode.updatedAt = Instant.parse("2026-01-02T00:00:00Z");

        UserIdentity identity = new UserIdentity();
        identity.id = UUID.randomUUID();
        identity.oauthProvider = "google";
        identity.canCreateJob = true;
        identity.canManageSkills = false;
        identity.lastActiveAt = Instant.parse("2026-01-03T00:00:00Z");

        MeshNodeConsent consent = new MeshNodeConsent();
        consent.scope = "professional_matching";
        consent.grantedAt = Instant.parse("2026-01-04T00:00:00Z");
        consent.policyVersion = "1.0";
        consent.revokedAt = null;

        MeshNode projectNode = new MeshNode();
        projectNode.id = UUID.randomUUID();
        projectNode.title = "Project";
        projectNode.description = "Build";
        projectNode.nodeType = org.peoplemesh.domain.enums.NodeType.PROJECT;
        projectNode.createdAt = Instant.parse("2026-01-05T00:00:00Z");

        AuditLogEntry auditEntry = new AuditLogEntry();
        auditEntry.action = "NODE_CREATED";
        auditEntry.toolName = "mesh_create_node";
        auditEntry.timestamp = Instant.parse("2026-01-06T00:00:00Z");
        service.auditEntries = List.of(auditEntry);

        when(profileService.getProfile(userId)).thenReturn(Optional.of(
                new ProfileSchema(null, null, null, null, null, null, null, null, null)
        ));

        try (var meshMock = mockStatic(MeshNode.class);
             var consentMock = mockStatic(MeshNodeConsent.class);
             var identityMock = mockStatic(UserIdentity.class)) {
            meshMock.when(() -> MeshNode.findPublishedUserNode(userId)).thenReturn(Optional.of(userNode));
            meshMock.when(() -> MeshNode.findByOwner(userId)).thenReturn(List.of(projectNode));
            consentMock.when(() -> MeshNodeConsent.findActiveByNodeId(userId)).thenReturn(List.of(consent));
            identityMock.when(() -> UserIdentity.findByNodeId(userId)).thenReturn(List.of(identity));

            String json = service.exportAllData(userId);

            assertTrue(json.contains("\"identities\""));
            assertTrue(json.contains("\"oauth_provider\" : \"google\""));
            assertTrue(json.contains("\"profile\""));
            assertTrue(json.contains("\"consents\""));
            assertTrue(json.contains("\"mesh_nodes\""));
            assertTrue(json.contains("\"audit_log\""));
            assertTrue(json.contains("\"exported_at\""));
        }

        verify(audit).log(userId, "DATA_EXPORTED", "gdpr_export");
    }

    @Test
    void exportAllData_whenNoNodeOrIdentities_returnsValidEmptySections() {
        TestableGdprService service = new TestableGdprService();
        service.audit = audit;
        service.profileService = profileService;
        service.objectMapper = new ObjectMapper();
        service.auditEntries = List.of();

        try (var meshMock = mockStatic(MeshNode.class);
             var consentMock = mockStatic(MeshNodeConsent.class);
             var identityMock = mockStatic(UserIdentity.class)) {
            meshMock.when(() -> MeshNode.findPublishedUserNode(userId)).thenReturn(Optional.empty());
            meshMock.when(() -> MeshNode.findByOwner(userId)).thenReturn(List.of());
            consentMock.when(() -> MeshNodeConsent.findActiveByNodeId(userId)).thenReturn(List.of());
            identityMock.when(() -> UserIdentity.findByNodeId(userId)).thenReturn(List.of());

            String json = service.exportAllData(userId);

            assertTrue(json.contains("\"profile\" : [ ]"));
            assertTrue(json.contains("\"consents\" : [ ]"));
            assertTrue(json.contains("\"mesh_nodes\" : [ ]"));
            assertTrue(json.contains("\"audit_log\" : [ ]"));
            assertFalse(json.contains("\"identities\""));
        }
    }

    private static final class TestableGdprService extends GdprService {
        List<AuditLogEntry> auditEntries = List.of();

        @Override
        List<AuditLogEntry> loadAuditEntries(String userIdHash) {
            return auditEntries;
        }
    }

}
