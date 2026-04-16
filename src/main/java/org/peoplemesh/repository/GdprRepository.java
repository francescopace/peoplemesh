package org.peoplemesh.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.peoplemesh.domain.enums.NodeType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class GdprRepository {

    @Inject
    EntityManager em;

    public void deleteNonUserNodesByOwner(UUID userId) {
        em.createQuery("DELETE FROM MeshNode n WHERE n.createdBy = :uid AND n.nodeType != :userType")
                .setParameter("uid", userId)
                .setParameter("userType", NodeType.USER)
                .executeUpdate();
    }

    public void deleteConsentsByNodeId(UUID userId) {
        em.createQuery("DELETE FROM MeshNodeConsent c WHERE c.nodeId = :uid")
                .setParameter("uid", userId)
                .executeUpdate();
    }

    public void deleteUserNode(UUID userId) {
        em.createQuery("DELETE FROM MeshNode n WHERE n.id = :uid AND n.nodeType = :userType")
                .setParameter("uid", userId)
                .setParameter("userType", NodeType.USER)
                .executeUpdate();
    }

    @SuppressWarnings("unchecked")
    public List<UUID> findInactiveUserIds(Instant threshold, int limit) {
        return em.createNativeQuery(
                        "SELECT mn.id FROM mesh.mesh_node mn " +
                                "WHERE mn.node_type = 'USER' " +
                                "AND NOT EXISTS (SELECT 1 FROM identity.user_identity ui " +
                                "WHERE ui.node_id = mn.id AND ui.last_active_at >= :threshold)",
                        UUID.class)
                .setParameter("threshold", threshold)
                .setMaxResults(limit)
                .getResultList();
    }
}
