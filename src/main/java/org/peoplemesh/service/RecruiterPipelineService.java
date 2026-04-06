package org.peoplemesh.service;

import org.peoplemesh.domain.dto.CandidatePipelineDto;
import org.peoplemesh.domain.dto.CandidatePipelineUpdate;
import org.peoplemesh.domain.enums.PipelineStage;
import org.peoplemesh.domain.model.CandidatePipelineEntry;
import org.peoplemesh.domain.model.JobPosting;
import org.peoplemesh.domain.model.UserProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class RecruiterPipelineService {

    @Inject
    AuditService auditService;

    @Transactional
    public CandidatePipelineDto addCandidate(UUID ownerUserId, UUID jobId, UUID candidateProfileId, CandidatePipelineUpdate update) {
        JobPosting job = ensureOwnedJob(ownerUserId, jobId);
        UserProfile candidateProfile = UserProfile.findById(candidateProfileId);
        if (candidateProfile == null || candidateProfile.isDeleted()) {
            throw new IllegalArgumentException("Candidate profile not found");
        }

        CandidatePipelineEntry entry = CandidatePipelineEntry.findByJobAndCandidate(job.id, candidateProfile.userId)
                .orElseGet(() -> {
                    CandidatePipelineEntry e = new CandidatePipelineEntry();
                    e.jobId = job.id;
                    e.candidateUserId = candidateProfile.userId;
                    return e;
                });

        applyUpdate(entry, update);
        entry.persist();
        auditService.log(ownerUserId, "PIPELINE_CANDIDATE_UPSERTED", "pipeline_upsert_candidate");
        return toDto(entry);
    }

    @Transactional
    public Optional<CandidatePipelineDto> updateCandidate(UUID ownerUserId, UUID jobId, UUID candidateUserId, CandidatePipelineUpdate update) {
        ensureOwnedJob(ownerUserId, jobId);
        return CandidatePipelineEntry.findByJobAndCandidate(jobId, candidateUserId)
                .map(entry -> {
                    applyUpdate(entry, update);
                    entry.persist();
                    auditService.log(ownerUserId, "PIPELINE_CANDIDATE_UPDATED", "pipeline_update_candidate");
                    return toDto(entry);
                });
    }

    public List<CandidatePipelineDto> listPipeline(UUID ownerUserId, UUID jobId, PipelineStage stage, boolean shortlistOnly) {
        ensureOwnedJob(ownerUserId, jobId);
        List<CandidatePipelineEntry> entries = stage == null
                ? CandidatePipelineEntry.findByJob(jobId)
                : CandidatePipelineEntry.findByJobAndStage(jobId, stage);
        return entries.stream()
                .filter(e -> !shortlistOnly || e.shortlisted)
                .map(this::toDto)
                .toList();
    }

    public List<CandidatePipelineDto> listInbox(UUID ownerUserId, UUID jobId) {
        return listPipeline(ownerUserId, jobId, PipelineStage.APPLIED, false);
    }

    private void applyUpdate(CandidatePipelineEntry entry, CandidatePipelineUpdate update) {
        if (update == null) {
            return;
        }
        if (update.stage() != null && update.stage() != entry.stage) {
            entry.stage = update.stage();
            entry.lastStageAt = Instant.now();
        }
        if (update.shortlisted() != null) {
            entry.shortlisted = update.shortlisted();
            if (Boolean.TRUE.equals(update.shortlisted()) && entry.stage == PipelineStage.APPLIED) {
                entry.stage = PipelineStage.SHORTLISTED;
                entry.lastStageAt = Instant.now();
            }
        }
        if (update.notes() != null) {
            entry.notes = update.notes();
        }
    }

    private JobPosting ensureOwnedJob(UUID ownerUserId, UUID jobId) {
        return JobPosting.findByIdAndOwner(jobId, ownerUserId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found"));
    }

    private CandidatePipelineDto toDto(CandidatePipelineEntry entry) {
        UUID profileId = UserProfile.findActiveByUserId(entry.candidateUserId).map(p -> p.id).orElse(null);
        return new CandidatePipelineDto(
                entry.id,
                entry.jobId,
                entry.candidateUserId,
                profileId,
                entry.stage,
                entry.shortlisted,
                entry.notes,
                entry.createdAt,
                entry.updatedAt,
                entry.lastStageAt
        );
    }
}
