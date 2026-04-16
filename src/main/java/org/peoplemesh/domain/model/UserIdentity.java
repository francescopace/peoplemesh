package org.peoplemesh.domain.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(name = "user_identity", schema = "identity")
public class UserIdentity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @Column(name = "node_id", nullable = false)
    public UUID nodeId;

    @Column(name = "oauth_provider", nullable = false)
    public String oauthProvider;

    @Column(name = "oauth_subject", nullable = false)
    public String oauthSubject;

    @Column(name = "is_admin", nullable = false)
    public boolean isAdmin;

    @Column(name = "last_active_at")
    public Instant lastActiveAt;

    public static Optional<UserIdentity> findByOauth(String provider, String subject) {
        return find("oauthProvider = ?1 and oauthSubject = ?2",
                provider, subject).firstResultOptional();
    }

    public static List<UserIdentity> findByNodeId(UUID nodeId) {
        return list("nodeId", nodeId);
    }
}
