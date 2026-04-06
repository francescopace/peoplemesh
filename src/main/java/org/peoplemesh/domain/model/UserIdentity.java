package org.peoplemesh.domain.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(name = "user_identity", schema = "identity")
public class UserIdentity extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @Column(name = "oauth_provider", nullable = false)
    public String oauthProvider;

    @Column(name = "oauth_subject", nullable = false)
    public String oauthSubject;

    @Column(name = "email_encrypted")
    public String emailEncrypted;

    @Version
    @Column(name = "version")
    public long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @Column(name = "deleted_at")
    public Instant deletedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public static Optional<UserIdentity> findByOauth(String provider, String subject) {
        return find("oauthProvider = ?1 and oauthSubject = ?2 and deletedAt is null",
                provider, subject).firstResultOptional();
    }

    public static Optional<UserIdentity> findActiveById(UUID id) {
        return find("id = ?1 and deletedAt is null", id).firstResultOptional();
    }
}
