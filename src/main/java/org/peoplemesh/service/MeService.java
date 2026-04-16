package org.peoplemesh.service;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.exception.ValidationBusinessException;
import org.peoplemesh.domain.model.MeshNode;
import org.peoplemesh.domain.model.UserIdentity;
import org.peoplemesh.repository.NodeRepository;
import org.peoplemesh.repository.UserIdentityRepository;

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

    public MeshNode findCurrentUserNode(UUID userId) {
        return nodeRepository.findPublishedUserNode(userId).orElse(null);
    }

    public void validateConsentScope(String scope, java.util.Collection<String> allowedScopes) {
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
