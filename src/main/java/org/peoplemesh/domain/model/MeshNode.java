package org.peoplemesh.domain.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import org.peoplemesh.domain.enums.NodeType;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    public static List<MeshNode> findByOwner(UUID createdBy) {
        return list("createdBy = ?1 order by updatedAt desc", createdBy);
    }

    public static List<MeshNode> findByOwnerAndType(UUID createdBy, NodeType type) {
        return list("createdBy = ?1 and nodeType = ?2 order by updatedAt desc", createdBy, type);
    }

    public static Optional<MeshNode> findByIdAndOwner(UUID id, UUID createdBy) {
        return find("id = ?1 and createdBy = ?2", id, createdBy).firstResultOptional();
    }

    public static List<MeshNode> findPublishedByType(NodeType type) {
        return list("nodeType = ?1 order by updatedAt desc", type);
    }

    /**
     * For USER nodes, created_by == id (self-ref). This finds the USER node for a given nodeId.
     */
    public static Optional<MeshNode> findPublishedUserNode(UUID nodeId) {
        return find("id = ?1 and nodeType = ?2",
                nodeId, NodeType.USER).firstResultOptional();
    }

    /**
     * Find a USER node by its external_id (email). Used during login to link
     * a new OAuth identity to an existing node.
     */
    public static Optional<MeshNode> findUserByExternalId(String externalId) {
        if (externalId == null || externalId.isBlank()) return Optional.empty();
        return find("externalId = ?1 and nodeType = ?2",
                externalId, NodeType.USER).firstResultOptional();
    }
}
