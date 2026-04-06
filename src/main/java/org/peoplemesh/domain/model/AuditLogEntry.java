package org.peoplemesh.domain.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_log", schema = "audit")
public class AuditLogEntry extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @Column(name = "user_id_hash", nullable = false)
    public String userIdHash;

    @Column(nullable = false)
    public String action;

    @Column(name = "tool_name")
    public String toolName;

    @Column(name = "timestamp", nullable = false)
    public Instant timestamp;

    @Column(name = "ip_hash")
    public String ipHash;

    @Column(name = "metadata_json", columnDefinition = "jsonb")
    public String metadataJson;

    @PrePersist
    void onCreate() {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }
}
