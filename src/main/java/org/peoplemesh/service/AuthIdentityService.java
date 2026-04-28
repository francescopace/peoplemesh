package org.peoplemesh.service;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.peoplemesh.domain.dto.AuthIdentityResponse;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.model.MeshNode;
import org.peoplemesh.domain.model.UserIdentity;
import org.peoplemesh.repository.NodeRepository;
import org.peoplemesh.repository.UserIdentityRepository;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class AuthIdentityService {

    @Inject
    NodeRepository nodeRepository;

    @Inject
    UserIdentityRepository userIdentityRepository;

    @Inject
    EntitlementService entitlementService;

    public Optional<AuthIdentityResponse> resolveCurrentIdentity(SecurityIdentity identity) {
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

    private AuthIdentityResponse buildPayload(UUID nodeId, String provider, String displayName, MeshNode node) {
        String photoUrl = readPhotoUrl(node);
        String safeDisplayName = normalizeString(displayName);
        return new AuthIdentityResponse(
                nodeId,
                normalizeString(provider),
                new AuthIdentityResponse.EntitlementsInfo(entitlementService.isAdmin(nodeId)),
                safeDisplayName,
                photoUrl
        );
    }

    private static String readPhotoUrl(MeshNode node) {
        if (node == null || node.structuredData == null) {
            return null;
        }
        Object raw = node.structuredData.get("avatar_url");
        if (!(raw instanceof String value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String normalizeString(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
