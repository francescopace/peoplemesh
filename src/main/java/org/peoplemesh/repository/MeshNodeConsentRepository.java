package org.peoplemesh.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.peoplemesh.domain.model.MeshNodeConsent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class MeshNodeConsentRepository {

    @Inject
    EntityManager em;

    public void persist(MeshNodeConsent consent) {
        em.persist(consent);
    }

    public boolean hasActiveConsent(UUID nodeId, String scope) {
        return !em.createQuery(
                        "SELECT c.id FROM MeshNodeConsent c " +
                                "WHERE c.nodeId = :nodeId AND c.scope = :scope AND c.revokedAt IS NULL",
                        UUID.class)
                .setParameter("nodeId", nodeId)
                .setParameter("scope", scope)
                .setMaxResults(1)
                .getResultList()
                .isEmpty();
    }

    public List<MeshNodeConsent> findActiveByNodeId(UUID nodeId) {
        return em.createQuery(
                        "FROM MeshNodeConsent c WHERE c.nodeId = :nodeId AND c.revokedAt IS NULL ORDER BY c.grantedAt DESC",
                        MeshNodeConsent.class)
                .setParameter("nodeId", nodeId)
                .getResultList();
    }

    public List<String> findActiveScopes(UUID nodeId) {
        return em.createQuery(
                        "SELECT DISTINCT c.scope FROM MeshNodeConsent c " +
                                "WHERE c.nodeId = :nodeId AND c.revokedAt IS NULL ORDER BY c.scope",
                        String.class)
                .setParameter("nodeId", nodeId)
                .getResultList();
    }

    public long revokeByNodeAndScope(UUID nodeId, String scope) {
        return em.createQuery(
                        "UPDATE MeshNodeConsent c SET c.revokedAt = :revokedAt " +
                                "WHERE c.nodeId = :nodeId AND c.scope = :scope AND c.revokedAt IS NULL")
                .setParameter("revokedAt", Instant.now())
                .setParameter("nodeId", nodeId)
                .setParameter("scope", scope)
                .executeUpdate();
    }

    public long revokeAllForNode(UUID nodeId) {
        return em.createQuery(
                        "UPDATE MeshNodeConsent c SET c.revokedAt = :revokedAt " +
                                "WHERE c.nodeId = :nodeId AND c.revokedAt IS NULL")
                .setParameter("revokedAt", Instant.now())
                .setParameter("nodeId", nodeId)
                .executeUpdate();
    }

    public long markAllRevoked(UUID nodeId, Instant revokedAt) {
        return em.createQuery(
                        "UPDATE MeshNodeConsent c SET c.revokedAt = :revokedAt " +
                                "WHERE c.nodeId = :nodeId AND c.revokedAt IS NULL")
                .setParameter("revokedAt", revokedAt)
                .setParameter("nodeId", nodeId)
                .executeUpdate();
    }
}
