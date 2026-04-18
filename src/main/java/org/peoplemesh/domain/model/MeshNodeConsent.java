package org.peoplemesh.domain.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
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

}
