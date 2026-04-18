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
        uniqueConstraints = @UniqueConstraint(columnNames = {"catalog_id", "name"}))
public class SkillDefinition extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @Column(name = "catalog_id", nullable = false)
    public UUID catalogId;

    @Column(nullable = false, length = 200)
    public String category;

    @Column(nullable = false, length = 200)
    public String name;

    @Column(columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    public List<String> aliases;

    @Column(name = "lxp_recommendation", length = 500)
    public String lxpRecommendation;

    @Column(columnDefinition = "vector")
    @JdbcTypeCode(SqlTypes.VECTOR)
    public float[] embedding;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

}
