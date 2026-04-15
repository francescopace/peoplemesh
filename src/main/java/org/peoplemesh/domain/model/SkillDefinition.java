package org.peoplemesh.domain.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
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

    public static List<SkillDefinition> findByCatalog(UUID catalogId) {
        return list("catalogId", catalogId);
    }

    public static List<SkillDefinition> findByCatalogAndCategory(UUID catalogId, String category) {
        return list("catalogId = ?1 and category = ?2", catalogId, category);
    }

    public static Optional<SkillDefinition> findByCatalogAndName(UUID catalogId, String name) {
        if (name == null) return Optional.empty();
        return find("catalogId = ?1 and lower(name) = ?2", catalogId, name.toLowerCase())
                .firstResultOptional();
    }

    public static long countByCatalog(UUID catalogId) {
        return count("catalogId", catalogId);
    }

    public static List<String> listCategories(UUID catalogId) {
        return getEntityManager()
                .createQuery("SELECT DISTINCT d.category FROM SkillDefinition d WHERE d.catalogId = ?1 ORDER BY d.category", String.class)
                .setParameter(1, catalogId)
                .getResultList();
    }
}
