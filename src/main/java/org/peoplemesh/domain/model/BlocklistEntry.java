package org.peoplemesh.domain.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "blocklist_entry", schema = "profiles",
       uniqueConstraints = @UniqueConstraint(columnNames = {"blocker_id", "blocked_id"}))
public class BlocklistEntry extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @Column(name = "blocker_id", nullable = false)
    public UUID blockerId;

    @Column(name = "blocked_id", nullable = false)
    public UUID blockedId;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public static boolean isBlocked(UUID userA, UUID userB) {
        return count("(blockerId = ?1 and blockedId = ?2) or (blockerId = ?2 and blockedId = ?1)",
                userA, userB) > 0;
    }
}
