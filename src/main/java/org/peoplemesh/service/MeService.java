package org.peoplemesh.service;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.exception.NotFoundBusinessException;
import org.peoplemesh.domain.exception.ValidationBusinessException;
import org.peoplemesh.domain.model.MeshNode;
import org.peoplemesh.domain.model.UserIdentity;
import org.peoplemesh.domain.dto.SkillAssessmentDto;
import org.peoplemesh.repository.NodeRepository;
import org.peoplemesh.repository.UserIdentityRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
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
        result.addAll(skillReconciliationService.reconcile(nodeId.get(), catalogId));
        return result;
    }

    public int updateCurrentUserSkillAssessments(UUID userId, List<SkillAssessmentDto> assessments) {
        UUID nodeId = findCurrentUserNodeId(userId)
                .orElseThrow(() -> new NotFoundBusinessException("No published profile found"));
        skillReconciliationService.applyReconciliation(nodeId, userId, assessments);
        return assessments.size();
    }

    public void validateConsentScope(String scope, Collection<String> allowedScopes) {
        if (!allowedScopes.contains(scope)) {
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
        entitlements.put("can_create_job", entitlementService.canCreateJob(nodeId));
        entitlements.put("can_manage_skills", entitlementService.canManageSkills(nodeId));
        payload.put("entitlements", entitlements);
        return payload;
    }
}
