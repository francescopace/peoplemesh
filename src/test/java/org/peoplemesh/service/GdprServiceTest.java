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
import org.peoplemesh.repository.AuditLogRepository;
import org.peoplemesh.repository.GdprRepository;
import org.peoplemesh.repository.MeshNodeConsentRepository;
import org.peoplemesh.repository.NodeRepository;
import org.peoplemesh.repository.UserIdentityRepository;

import jakarta.persistence.EntityManager;

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
    @Mock NodeRepository nodeRepository;
    @Mock UserIdentityRepository userIdentityRepository;
    @Mock MeshNodeConsentRepository meshNodeConsentRepository;
    @Mock GdprRepository gdprRepository;
    @Mock AuditLogRepository auditLogRepository;

    @InjectMocks
    GdprService gdprService;

    private final UUID userId = UUID.randomUUID();

    @Test
    void deleteAllData_hardDeletesAllUserData() {
        gdprService.deleteAllData(userId);

        verify(audit).log(userId, "ACCOUNT_DELETED", "gdpr_delete");
        verify(gdprRepository).deleteConsentsByNodeId(userId);
        verify(gdprRepository).deleteUserNode(userId);
    }

    @Test
    void getPrivacyDashboard_noNode_returnsDefaults() {
        when(nodeRepository.findPublishedUserNode(userId)).thenReturn(Optional.empty());
        when(meshNodeConsentRepository.findActiveByNodeId(userId)).thenReturn(Collections.emptyList());

        PrivacyDashboard dashboard = gdprService.getPrivacyDashboard(userId);

        assertNotNull(dashboard);
        assertNull(dashboard.lastProfileUpdate());
        assertFalse(dashboard.searchable());
        assertEquals(0, dashboard.activeConsents());
        assertTrue(dashboard.consentScopes().isEmpty());
    }

    @Test
    void getPrivacyDashboard_withNode_returnsData() {
        MeshNode node = new MeshNode();
        node.updatedAt = Instant.now();
        node.searchable = true;
        when(nodeRepository.findPublishedUserNode(userId)).thenReturn(Optional.of(node));

        MeshNodeConsent consent = new MeshNodeConsent();
        consent.scope = "professional_matching";
        consent.grantedAt = Instant.now();
        consent.policyVersion = "1.0";
        when(meshNodeConsentRepository.findActiveByNodeId(userId)).thenReturn(List.of(consent));

        PrivacyDashboard dashboard = gdprService.getPrivacyDashboard(userId);

        assertNotNull(dashboard.lastProfileUpdate());
        assertTrue(dashboard.searchable());
        assertEquals(1, dashboard.activeConsents());
        assertEquals(List.of("professional_matching"), dashboard.consentScopes());
    }

    @Test
    void getPrivacyDashboard_distinctScopes() {
        when(nodeRepository.findPublishedUserNode(userId)).thenReturn(Optional.empty());
        MeshNodeConsent c1 = new MeshNodeConsent(); c1.scope = "professional_matching"; c1.grantedAt = Instant.now(); c1.policyVersion = "1.0";
        MeshNodeConsent c2 = new MeshNodeConsent(); c2.scope = "embedding_processing"; c2.grantedAt = Instant.now(); c2.policyVersion = "1.0";
        when(meshNodeConsentRepository.findActiveByNodeId(userId)).thenReturn(List.of(c1, c2));

        PrivacyDashboard dashboard = gdprService.getPrivacyDashboard(userId);

        assertEquals(2, dashboard.activeConsents());
        assertEquals(2, dashboard.consentScopes().size());
    }

    @Test
    void enforceRetention_deletesInactiveUsers() {
        UUID inactiveUser = UUID.randomUUID();
        when(gdprRepository.findInactiveUserIds(any(), anyInt())).thenReturn(List.of(inactiveUser));

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
        service.nodeRepository = nodeRepository;
        service.userIdentityRepository = userIdentityRepository;
        service.meshNodeConsentRepository = meshNodeConsentRepository;

        MeshNode userNode = new MeshNode();
        userNode.id = userId;
        userNode.externalId = "user@example.com";
        userNode.createdAt = Instant.parse("2026-01-01T00:00:00Z");
        userNode.updatedAt = Instant.parse("2026-01-02T00:00:00Z");

        UserIdentity identity = new UserIdentity();
        identity.id = UUID.randomUUID();
        identity.oauthProvider = "google";
        identity.isAdmin = false;
        identity.lastActiveAt = Instant.parse("2026-01-03T00:00:00Z");

        MeshNodeConsent consent = new MeshNodeConsent();
        consent.scope = "professional_matching";
        consent.grantedAt = Instant.parse("2026-01-04T00:00:00Z");
        consent.policyVersion = "1.0";
        consent.revokedAt = null;

        AuditLogEntry auditEntry = new AuditLogEntry();
        auditEntry.action = "NODE_CREATED";
        auditEntry.toolName = "mesh_create_node";
        auditEntry.timestamp = Instant.parse("2026-01-06T00:00:00Z");
        service.auditEntries = List.of(auditEntry);

        when(profileService.getProfile(userId)).thenReturn(Optional.of(
                new ProfileSchema(null, null, null, null, null, null, null, null, null, null)
        ));

        when(nodeRepository.findPublishedUserNode(userId)).thenReturn(Optional.of(userNode));
        when(meshNodeConsentRepository.findActiveByNodeId(userId)).thenReturn(List.of(consent));
        when(userIdentityRepository.findByNodeId(userId)).thenReturn(List.of(identity));

        String json = service.exportAllData(userId);

        assertTrue(json.contains("\"identities\""));
        assertTrue(json.contains("\"oauth_provider\" : \"google\""));
        assertTrue(json.contains("\"profile\""));
        assertTrue(json.contains("\"consents\""));
        assertTrue(json.contains("\"mesh_nodes\""));
        assertTrue(json.contains("\"audit_log\""));
        assertTrue(json.contains("\"exported_at\""));

        verify(audit).log(userId, "DATA_EXPORTED", "gdpr_export");
    }

    @Test
    void exportAllData_whenNoNodeOrIdentities_returnsValidEmptySections() {
        TestableGdprService service = new TestableGdprService();
        service.audit = audit;
        service.profileService = profileService;
        service.objectMapper = new ObjectMapper();
        service.nodeRepository = nodeRepository;
        service.userIdentityRepository = userIdentityRepository;
        service.meshNodeConsentRepository = meshNodeConsentRepository;
        service.auditEntries = List.of();

        when(nodeRepository.findPublishedUserNode(userId)).thenReturn(Optional.empty());
        when(meshNodeConsentRepository.findActiveByNodeId(userId)).thenReturn(List.of());
        when(userIdentityRepository.findByNodeId(userId)).thenReturn(List.of());

        String json = service.exportAllData(userId);

        assertTrue(json.contains("\"profile\" : [ ]"));
        assertTrue(json.contains("\"consents\" : [ ]"));
        assertTrue(json.contains("\"mesh_nodes\" : [ ]"));
        assertTrue(json.contains("\"audit_log\" : [ ]"));
        assertFalse(json.contains("\"identities\""));
    }

    private static final class TestableGdprService extends GdprService {
        List<AuditLogEntry> auditEntries = List.of();

        @Override
        List<AuditLogEntry> loadAuditEntries(String userIdHash) {
            return auditEntries;
        }
    }

}
