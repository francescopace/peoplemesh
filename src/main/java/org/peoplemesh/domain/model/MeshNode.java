package org.peoplemesh.domain.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import org.peoplemesh.domain.enums.NodeType;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "mesh_node", schema = "mesh")
public class MeshNode extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "created_by", nullable = false)
    public UUID createdBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "node_type", nullable = false)
    public NodeType nodeType;

    @Column(nullable = false, length = 200)
    public String title;

    @Column(nullable = false)
    public String description;

    @Column(name = "external_id")
    public String externalId;

    @Column(name = "tags", columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    public List<String> tags;

    @Column(name = "structured_data", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    public Map<String, Object> structuredData;

    public String country;

    @Column(name = "embedding", columnDefinition = "vector")
    @JdbcTypeCode(SqlTypes.VECTOR)
    public float[] embedding;

    public boolean searchable = true;

    @Column(name = "closed_at")
    public Instant closedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (nodeType == NodeType.USER && createdBy == null) {
            createdBy = id;
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

}
