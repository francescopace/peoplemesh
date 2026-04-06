package org.peoplemesh.domain.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "connection", schema = "profiles")
public class Connection extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @Column(name = "user_a_id", nullable = false)
    public UUID userAId;

    @Column(name = "user_b_id", nullable = false)
    public UUID userBId;

    @Column(name = "shared_contact_a_encrypted")
    public String sharedContactAEncrypted;

    @Column(name = "shared_contact_b_encrypted")
    public String sharedContactBEncrypted;

    @Column(name = "connected_at", nullable = false)
    public Instant connectedAt;

    @PrePersist
    void onCreate() {
        connectedAt = Instant.now();
    }

    public static List<Connection> findByUserId(UUID userId) {
        return list("userAId = ?1 or userBId = ?1", userId);
    }

    public static boolean existsBetween(UUID userA, UUID userB) {
        return count("(userAId = ?1 and userBId = ?2) or (userAId = ?2 and userBId = ?1)",
                userA, userB) > 0;
    }
}
