package org.peoplemesh.service;

import io.quarkus.security.identity.SecurityIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.domain.dto.ProfileSchema;
import org.peoplemesh.domain.dto.SkillAssessmentDto;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.exception.NotFoundBusinessException;
import org.peoplemesh.domain.exception.ValidationBusinessException;
import org.peoplemesh.domain.model.MeshNode;
import org.peoplemesh.domain.model.UserIdentity;
import org.peoplemesh.repository.NodeRepository;
import org.peoplemesh.repository.UserIdentityRepository;

import java.security.Principal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeServiceTest {

    @Mock
    NodeRepository nodeRepository;
    @Mock
    UserIdentityRepository userIdentityRepository;
    @Mock
    EntitlementService entitlementService;
    @Mock
    SkillAssessmentService skillAssessmentService;
    @Mock
    SkillReconciliationService skillReconciliationService;
    @Mock
    ProfileService profileService;
    @Mock
    ConsentService consentService;
    @Mock
    AuditService auditService;

    @InjectMocks
    MeService service;

    @Test
    void listCurrentUserSkillAssessments_mergesExistingAndSuggested() {
        UUID userId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        UUID catalogId = UUID.randomUUID();
        UUID existingSkillId = UUID.randomUUID();
        UUID suggestedSkillId = UUID.randomUUID();

        MeshNode userNode = new MeshNode();
        userNode.id = nodeId;
        userNode.nodeType = NodeType.USER;
        when(nodeRepository.findPublishedUserNode(userId)).thenReturn(Optional.of(userNode));

        SkillAssessmentDto existing = SkillAssessmentDto.forInput(existingSkillId, 3, true);
        SkillAssessmentDto suggested = SkillAssessmentDto.suggestion(
                suggestedSkillId, "Python", "Backend", "semantic", 0.91);
        when(skillAssessmentService.listAssessments(nodeId, catalogId)).thenReturn(List.of(existing));
        when(skillReconciliationService.reconcile(eq(nodeId), eq(catalogId), eq(Set.of(existingSkillId))))
                .thenReturn(List.of(suggested));

        List<SkillAssessmentDto> result = service.listCurrentUserSkillAssessments(userId, catalogId);

        assertEquals(2, result.size());
        assertEquals(existingSkillId, result.get(0).skillId());
        assertEquals(suggestedSkillId, result.get(1).skillId());
    }

    @Test
    void updateCurrentUserSkillAssessments_withoutPublishedProfile_throwsNotFound() {
        UUID userId = UUID.randomUUID();
        when(nodeRepository.findPublishedUserNode(userId)).thenReturn(Optional.empty());

        assertThrows(NotFoundBusinessException.class,
                () -> service.updateCurrentUserSkillAssessments(userId, List.of()));
    }

    @Test
    void applySelectiveImport_blankSource_throwsValidationError() {
        ProfileSchema selectedFields = mock(ProfileSchema.class);

        assertThrows(ValidationBusinessException.class,
                () -> service.applySelectiveImport(UUID.randomUUID(), selectedFields, "  "));
    }

    @Test
    void applySelectiveImport_trimsSourceBeforeDelegating() {
        UUID userId = UUID.randomUUID();
        ProfileSchema selectedFields = mock(ProfileSchema.class);

        service.applySelectiveImport(userId, selectedFields, " linkedin ");

        verify(profileService).applySelectiveImport(userId, selectedFields, "linkedin");
    }

    @Test
    void grantConsent_recordsConsentAndAudit() {
        UUID userId = UUID.randomUUID();
        Collection<String> allowedScopes = List.of("professional_matching", "analytics");

        service.grantConsent(userId, "professional_matching", allowedScopes, "hash");

        verify(consentService).recordConsent(userId, "professional_matching", "hash");
        verify(auditService).log(userId, "CONSENT_GRANTED", "privacy_consent");
    }

    @Test
    void revokeConsent_invalidScope_throwsValidationError() {
        UUID userId = UUID.randomUUID();
        Collection<String> allowedScopes = List.of("professional_matching");

        assertThrows(ValidationBusinessException.class,
                () -> service.revokeConsent(userId, "unknown_scope", allowedScopes));
    }

    @Test
    void resolveIdentityPayload_withUserIdAttribute_returnsPayload() {
        UUID nodeId = UUID.randomUUID();
        SecurityIdentity identity = mock(SecurityIdentity.class);
        MeshNode node = new MeshNode();
        node.id = nodeId;
        node.nodeType = NodeType.USER;
        node.externalId = "mail@test.com";
        when(identity.getAttribute("pm.userId")).thenReturn(nodeId);
        when(identity.getAttribute("pm.provider")).thenReturn("google");
        when(identity.getAttribute("pm.displayName")).thenReturn("Alice");
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(node));
        when(entitlementService.isAdmin(nodeId)).thenReturn(true);

        Optional<Map<String, Object>> payload = service.resolveIdentityPayload(identity);
        assertTrue(payload.isPresent());
        assertEquals(nodeId, payload.get().get("user_id"));
    }

    @Test
    void resolveIdentityPayload_fallbackByOauthSubject() {
        SecurityIdentity identity = mock(SecurityIdentity.class);
        Principal principal = mock(Principal.class);
        UUID nodeId = UUID.randomUUID();
        MeshNode node = new MeshNode();
        node.id = nodeId;
        node.nodeType = NodeType.USER;
        UserIdentity linked = new UserIdentity();
        linked.nodeId = nodeId;
        linked.oauthProvider = "microsoft";

        when(identity.getAttribute("pm.userId")).thenReturn(null);
        when(identity.isAnonymous()).thenReturn(false);
        when(identity.getPrincipal()).thenReturn(principal);
        when(principal.getName()).thenReturn("sub-1");
        when(userIdentityRepository.findByOauthSubject("sub-1")).thenReturn(Optional.of(linked));
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(node));
        when(entitlementService.isAdmin(nodeId)).thenReturn(false);

        assertTrue(service.resolveIdentityPayload(identity).isPresent());
    }

    @Test
    void updateCurrentUserSkillAssessments_success_appliesReconciliation() {
        UUID userId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        MeshNode userNode = new MeshNode();
        userNode.id = nodeId;
        userNode.nodeType = NodeType.USER;
        when(nodeRepository.findPublishedUserNode(userId)).thenReturn(Optional.of(userNode));

        List<SkillAssessmentDto> updates = List.of(SkillAssessmentDto.forInput(UUID.randomUUID(), 2, true));
        int count = service.updateCurrentUserSkillAssessments(userId, updates);

        assertEquals(1, count);
        verify(skillReconciliationService).applyReconciliation(nodeId, userId, updates);
    }

    @Test
    void revokeConsent_validScope_delegatesAndAudits() {
        UUID userId = UUID.randomUUID();
        service.revokeConsent(userId, "professional_matching", List.of("professional_matching"));
        verify(consentService).revokeConsent(userId, "professional_matching");
        verify(auditService).log(userId, "CONSENT_REVOKED", "privacy_consent");
    }

    @Test
    void getActiveConsentScopes_returnsServiceValues() {
        UUID userId = UUID.randomUUID();
        when(consentService.getActiveScopes(userId)).thenReturn(List.of("professional_matching"));
        assertEquals(List.of("professional_matching"), service.getActiveConsentScopes(userId));
    }

    @Test
    void resolveProfile_upsertsAndReturnsPersistedProfile() {
        UUID userId = UUID.randomUUID();
        ProfileSchema updates = mock(ProfileSchema.class);
        ProfileSchema persisted = mock(ProfileSchema.class);
        when(profileService.getProfile(userId)).thenReturn(Optional.of(persisted));

        ProfileSchema result = service.resolveProfile(userId, updates);

        verify(profileService).upsertProfile(userId, updates);
        assertEquals(persisted, result);
    }

    @Test
    void getConsentView_containsDefaultScopesAndActiveScopes() {
        UUID userId = UUID.randomUUID();
        when(consentService.getActiveScopes(userId)).thenReturn(List.of("professional_matching"));

        Map<String, Object> view = service.getConsentView(userId);

        assertEquals(ConsentService.DEFAULT_CONSENT_SCOPES, view.get("scopes"));
        assertEquals(List.of("professional_matching"), view.get("active"));
    }
}
