package org.peoplemesh.service;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.exception.NotFoundBusinessException;
import org.peoplemesh.domain.exception.ValidationBusinessException;
import org.peoplemesh.domain.model.MeshNode;
import org.peoplemesh.domain.model.UserIdentity;
import org.peoplemesh.domain.dto.ProfileSchema;
import org.peoplemesh.domain.dto.SkillAssessmentDto;
import org.peoplemesh.repository.NodeRepository;
import org.peoplemesh.repository.UserIdentityRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class MeService {

    @Inject
    NodeRepository nodeRepository;

    @Inject
    UserIdentityRepository userIdentityRepository;

    @Inject
    EntitlementService entitlementService;

    @Inject
    SkillAssessmentService skillAssessmentService;

    @Inject
    SkillReconciliationService skillReconciliationService;

    @Inject
    ProfileService profileService;

    @Inject
    ConsentService consentService;

    @Inject
    AuditService auditService;

    public Optional<Map<String, Object>> resolveIdentityPayload(SecurityIdentity identity) {
        UUID userId = identity.<UUID>getAttribute("pm.userId");
        String provider = identity.<String>getAttribute("pm.provider");
        String displayName = identity.<String>getAttribute("pm.displayName");
        if (userId != null) {
            return nodeRepository.findById(userId)
                    .filter(node -> node.nodeType == NodeType.USER)
                    .map(node -> buildPayload(userId, provider, displayName, node));
        }
        if (identity.isAnonymous()) {
            return Optional.empty();
        }
        String subject = identity.getPrincipal().getName();
        Optional<UserIdentity> linkedIdentity = userIdentityRepository.findByOauthSubject(subject);
        return linkedIdentity
                .flatMap(user -> nodeRepository.findById(user.nodeId)
                        .map(node -> buildPayload(user.nodeId, user.oauthProvider, null, node)));
    }

    public Optional<UUID> findCurrentUserNodeId(UUID userId) {
        return nodeRepository.findPublishedUserNode(userId).map(node -> node.id);
    }

    public List<SkillAssessmentDto> listCurrentUserSkillAssessments(UUID userId, UUID catalogId) {
        Optional<UUID> nodeId = findCurrentUserNodeId(userId);
        if (nodeId.isEmpty()) {
            return List.of();
        }
        List<SkillAssessmentDto> result = new ArrayList<>(skillAssessmentService.listAssessments(nodeId.get(), catalogId));
        Set<UUID> existingIds = new HashSet<>();
        for (SkillAssessmentDto assessment : result) {
            if (assessment.skillId() != null) {
                existingIds.add(assessment.skillId());
            }
        }
        result.addAll(skillReconciliationService.reconcile(nodeId.get(), catalogId, existingIds));
        return result;
    }

    public int updateCurrentUserSkillAssessments(UUID userId, List<SkillAssessmentDto> assessments) {
        UUID nodeId = findCurrentUserNodeId(userId)
                .orElseThrow(() -> new NotFoundBusinessException("No published profile found"));
        skillReconciliationService.applyReconciliation(nodeId, userId, assessments);
        return assessments.size();
    }

    public void applySelectiveImport(UUID userId, ProfileSchema selectedFields, String source) {
        if (source == null || source.isBlank()) {
            throw new ValidationBusinessException("Missing source parameter");
        }
        profileService.applySelectiveImport(userId, selectedFields, source.trim());
    }

    public void grantConsent(UUID userId, String scope, Collection<String> allowedScopes, String clientIpHash) {
        validateConsentScope(scope, allowedScopes);
        consentService.recordConsent(userId, scope, clientIpHash);
        auditService.log(userId, "CONSENT_GRANTED", "privacy_consent");
    }

    public void revokeConsent(UUID userId, String scope, Collection<String> allowedScopes) {
        validateConsentScope(scope, allowedScopes);
        consentService.revokeConsent(userId, scope);
        auditService.log(userId, "CONSENT_REVOKED", "privacy_consent");
    }

    public List<String> getActiveConsentScopes(UUID userId) {
        return consentService.getActiveScopes(userId);
    }

    public void validateConsentScope(String scope, Collection<String> allowedScopes) {
        if (scope == null || scope.isBlank() || !allowedScopes.contains(scope)) {
            throw new ValidationBusinessException("Invalid consent scope: " + scope);
        }
    }

    private Map<String, Object> buildPayload(UUID nodeId, String provider, String displayName, MeshNode node) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("user_id", nodeId);
        payload.put("provider", provider);
        payload.put("email_present", node.externalId != null && !node.externalId.isBlank());
        payload.put("profile_id", node.id);
        if (displayName != null && !displayName.isBlank()) {
            payload.put("display_name", displayName);
        }
        Map<String, Boolean> entitlements = new LinkedHashMap<>();
        entitlements.put("is_admin", entitlementService.isAdmin(nodeId));
        payload.put("entitlements", entitlements);
        return payload;
    }
}
