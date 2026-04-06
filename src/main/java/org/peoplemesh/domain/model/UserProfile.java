package org.peoplemesh.domain.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import org.peoplemesh.domain.enums.*;
import jakarta.persistence.*;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(name = "user_profile", schema = "profiles")
public class UserProfile extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    public UUID userId;

    @Column(name = "profile_version")
    public String profileVersion;

    @Column(name = "roles_encrypted")
    public String rolesEncrypted;

    @Enumerated(EnumType.STRING)
    public Seniority seniority;

    @Column(name = "industries_encrypted")
    public String industriesEncrypted;

    @Column(name = "skills_technical", columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    public List<String> skillsTechnical;

    @Column(name = "skills_soft", columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    public List<String> skillsSoft;

    @Column(name = "tools_and_tech", columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    public List<String> toolsAndTech;

    @Column(name = "languages_spoken", columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    public List<String> languagesSpoken;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_mode")
    public WorkMode workMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type")
    public EmploymentType employmentType;

    @Column(name = "topics_frequent", columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    public List<String> topicsFrequent;

    @Column(name = "learning_areas", columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    public List<String> learningAreas;

    @Column(name = "project_types", columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    public List<String> projectTypes;

    @Column(name = "collaboration_goals", columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Enumerated(EnumType.STRING)
    public List<CollaborationGoal> collaborationGoals;

    public String country;

    @Column(name = "city_encrypted")
    public String cityEncrypted;

    public String timezone;

    @Column(name = "show_city")
    public boolean showCity;

    @Column(name = "show_country")
    public boolean showCountry;

    public boolean searchable;

    @Column(name = "contact_via")
    public String contactVia;

    @Column(name = "embedding", columnDefinition = "vector(1536)")
    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = 1536)
    public float[] embedding;

    @Version
    @Column(name = "version")
    public long version;

    @Column(name = "generated_at")
    public Instant generatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @Column(name = "deleted_at")
    public Instant deletedAt;

    @Column(name = "last_active_at")
    public Instant lastActiveAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
        lastActiveAt = createdAt;
        if (contactVia == null) {
            contactVia = "platform_only";
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public static Optional<UserProfile> findActiveByUserId(UUID userId) {
        return find("userId = ?1 and deletedAt is null", userId).firstResultOptional();
    }
}
