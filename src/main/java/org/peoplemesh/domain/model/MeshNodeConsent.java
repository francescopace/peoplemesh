package org.peoplemesh.domain.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "mesh_node_consent", schema = "mesh")
public class MeshNodeConsent extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @Column(name = "node_id", nullable = false)
    public UUID nodeId;

    @Column(nullable = false)
    public String scope;

    @Column(name = "granted_at", nullable = false)
    public Instant grantedAt;

    @Column(name = "ip_hash")
    public String ipHash;

    @Column(name = "policy_version", nullable = false)
    public String policyVersion;

    @Column(name = "revoked_at")
    public Instant revokedAt;

    public boolean isActive() {
        return revokedAt == null;
    }

    public static List<MeshNodeConsent> findActiveByNodeId(UUID nodeId) {
        return list("nodeId = ?1 and revokedAt is null", nodeId);
    }

    public static List<String> findActiveScopesByNodeId(UUID nodeId) {
        return findActiveByNodeId(nodeId).stream()
                .map(c -> c.scope)
                .distinct()
                .toList();
    }

    public static long revokeByNodeAndScope(UUID nodeId, String scope) {
        return update("revokedAt = ?1 where nodeId = ?2 and scope = ?3 and revokedAt is null",
                Instant.now(), nodeId, scope);
    }

    public static long revokeAllForNode(UUID nodeId) {
        return update("revokedAt = ?1 where nodeId = ?2 and revokedAt is null",
                Instant.now(), nodeId);
    }
}
