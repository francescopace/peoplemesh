package org.peoplemesh.repository;

import jakarta.enterprise.context.ApplicationScoped;
import org.peoplemesh.domain.model.MeshNodeConsent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class MeshNodeConsentRepository {

    public void persist(MeshNodeConsent consent) {
        consent.persist();
    }

    public boolean hasActiveConsent(UUID nodeId, String scope) {
        return MeshNodeConsent.count("nodeId = ?1 and scope = ?2 and revokedAt is null", nodeId, scope) > 0;
    }

    public List<String> findActiveScopes(UUID nodeId) {
        return MeshNodeConsent.findActiveScopesByNodeId(nodeId);
    }

    public long revokeByNodeAndScope(UUID nodeId, String scope) {
        return MeshNodeConsent.revokeByNodeAndScope(nodeId, scope);
    }

    public long revokeAllForNode(UUID nodeId) {
        return MeshNodeConsent.revokeAllForNode(nodeId);
    }

    public long markAllRevoked(UUID nodeId, Instant revokedAt) {
        return MeshNodeConsent.update("revokedAt = ?1 where nodeId = ?2 and revokedAt is null", revokedAt, nodeId);
    }
}
