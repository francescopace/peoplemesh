package org.peoplemesh.domain.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
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

}
