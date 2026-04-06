package org.peoplemesh.domain.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "profile_consent", schema = "profiles")
public class ProfileConsent extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @Column(name = "user_id", nullable = false)
    public UUID userId;

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

    public static List<ProfileConsent> findActiveByUserId(UUID userId) {
        return list("userId = ?1 and revokedAt is null", userId);
    }

    public static long revokeAllForUser(UUID userId) {
        return update("revokedAt = ?1 where userId = ?2 and revokedAt is null",
                Instant.now(), userId);
    }
}
