package org.peoplemesh.domain.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "skill_definition", schema = "skills",
        uniqueConstraints = @UniqueConstraint(columnNames = {"name"}))
public class SkillDefinition extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @Column(nullable = false, length = 200)
    public String name;

    @Column(nullable = false, columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    public List<String> aliases;

    @Column(name = "usage_count", nullable = false)
    public int usageCount;

    @Column(columnDefinition = "vector(384)")
    @JdbcTypeCode(SqlTypes.VECTOR)
    public float[] embedding;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (aliases == null) {
            aliases = List.of();
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
        if (aliases == null) {
            aliases = List.of();
        }
    }

}
