package org.peoplemesh.domain.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import org.peoplemesh.domain.enums.EmploymentType;
import org.peoplemesh.domain.enums.JobStatus;
import org.peoplemesh.domain.enums.WorkMode;
import jakarta.persistence.*;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(name = "job_posting", schema = "jobs")
public class JobPosting extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @Column(name = "owner_user_id", nullable = false)
    public UUID ownerUserId;

    @Column(nullable = false, length = 150)
    public String title;

    @Column(nullable = false)
    public String description;

    @Column(name = "requirements_text")
    public String requirementsText;

    @Column(name = "skills_required", columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    public List<String> skillsRequired;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_mode")
    public WorkMode workMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type")
    public EmploymentType employmentType;

    public String country;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public JobStatus status;

    @Column(name = "embedding", columnDefinition = "vector(1536)")
    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = 1536)
    public float[] embedding;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @Column(name = "published_at")
    public Instant publishedAt;

    @Column(name = "closed_at")
    public Instant closedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) {
            status = JobStatus.DRAFT;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public static List<JobPosting> findByOwner(UUID ownerUserId) {
        return list("ownerUserId = ?1 order by updatedAt desc", ownerUserId);
    }

    public static Optional<JobPosting> findByIdAndOwner(UUID id, UUID ownerUserId) {
        return find("id = ?1 and ownerUserId = ?2", id, ownerUserId).firstResultOptional();
    }
}
