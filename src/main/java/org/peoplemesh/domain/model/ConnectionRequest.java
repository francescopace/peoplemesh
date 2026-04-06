package org.peoplemesh.domain.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import org.peoplemesh.domain.enums.ConnectionStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(name = "connection_request", schema = "profiles")
public class ConnectionRequest extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @Column(name = "from_user_id", nullable = false)
    public UUID fromUserId;

    @Column(name = "to_user_id", nullable = false)
    public UUID toUserId;

    @Column(name = "message_encrypted")
    public String messageEncrypted;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public ConnectionStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "responded_at")
    public Instant respondedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        status = ConnectionStatus.PENDING;
    }

    public static List<ConnectionRequest> findPendingForUser(UUID userId) {
        return list("toUserId = ?1 and status = ?2", userId, ConnectionStatus.PENDING);
    }

    public static Optional<ConnectionRequest> findByIdAndRecipient(UUID id, UUID userId) {
        return find("id = ?1 and toUserId = ?2", id, userId).firstResultOptional();
    }

    public static long countTodayByUser(UUID userId) {
        Instant startOfDay = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        return count("fromUserId = ?1 and createdAt >= ?2", userId, startOfDay);
    }

    public static boolean existsBetween(UUID userA, UUID userB) {
        return count("(fromUserId = ?1 and toUserId = ?2) or (fromUserId = ?2 and toUserId = ?1)",
                userA, userB) > 0;
    }
}
