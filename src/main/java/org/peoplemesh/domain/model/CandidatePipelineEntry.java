package org.peoplemesh.domain.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import org.peoplemesh.domain.enums.PipelineStage;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(name = "candidate_pipeline_entry", schema = "jobs")
public class CandidatePipelineEntry extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @Column(name = "job_id", nullable = false)
    public UUID jobId;

    @Column(name = "candidate_user_id", nullable = false)
    public UUID candidateUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public PipelineStage stage;

    @Column(nullable = false)
    public boolean shortlisted;

    @Column(name = "notes")
    public String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @Column(name = "last_stage_at", nullable = false)
    public Instant lastStageAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        lastStageAt = now;
        if (stage == null) {
            stage = PipelineStage.APPLIED;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public static Optional<CandidatePipelineEntry> findByJobAndCandidate(UUID jobId, UUID candidateUserId) {
        return find("jobId = ?1 and candidateUserId = ?2", jobId, candidateUserId).firstResultOptional();
    }

    public static List<CandidatePipelineEntry> findByJob(UUID jobId) {
        return list("jobId = ?1 order by updatedAt desc", jobId);
    }

    public static List<CandidatePipelineEntry> findByJobAndStage(UUID jobId, PipelineStage stage) {
        return list("jobId = ?1 and stage = ?2 order by updatedAt desc", jobId, stage);
    }
}
