package org.peoplemesh.service;

import org.peoplemesh.domain.dto.JobPostingDto;
import org.peoplemesh.domain.dto.JobPostingPayload;
import org.peoplemesh.domain.enums.JobStatus;
import org.peoplemesh.domain.model.JobPosting;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ApplicationScoped
public class JobService {

    @Inject
    EmbeddingService embeddingService;

    @Inject
    AuditService auditService;

    @Transactional
    public JobPostingDto createJob(UUID ownerUserId, JobPostingPayload payload) {
        validatePayload(payload);
        JobPosting job = new JobPosting();
        job.ownerUserId = ownerUserId;
        applyPayload(job, payload);
        job.status = JobStatus.DRAFT;
        job.embedding = embeddingService.generateEmbedding(jobToText(job));
        job.persist();
        auditService.log(ownerUserId, "JOB_CREATED", "job_create");
        return toDto(job);
    }

    @Transactional
    public Optional<JobPostingDto> updateJob(UUID ownerUserId, UUID jobId, JobPostingPayload payload) {
        validatePayload(payload);
        return JobPosting.findByIdAndOwner(jobId, ownerUserId)
                .map(job -> {
                    applyPayload(job, payload);
                    job.embedding = embeddingService.generateEmbedding(jobToText(job));
                    job.persist();
                    auditService.log(ownerUserId, "JOB_UPDATED", "job_update");
                    return toDto(job);
                });
    }

    public Optional<JobPostingDto> getJob(UUID ownerUserId, UUID jobId) {
        return JobPosting.findByIdAndOwner(jobId, ownerUserId).map(this::toDto);
    }

    public List<JobPostingDto> listJobs(UUID ownerUserId) {
        return JobPosting.findByOwner(ownerUserId).stream().map(this::toDto).toList();
    }

    @Transactional
    public Optional<JobPostingDto> transitionStatus(UUID ownerUserId, UUID jobId, JobStatus targetStatus) {
        if (targetStatus == null) {
            throw new IllegalArgumentException("target status is required");
        }
        return JobPosting.findByIdAndOwner(jobId, ownerUserId)
                .map(job -> {
                    validateTransition(job.status, targetStatus);
                    job.status = targetStatus;
                    Instant now = Instant.now();
                    if (targetStatus == JobStatus.PUBLISHED && job.publishedAt == null) {
                        job.publishedAt = now;
                    }
                    if (targetStatus == JobStatus.CLOSED || targetStatus == JobStatus.FILLED) {
                        job.closedAt = now;
                    } else {
                        job.closedAt = null;
                    }
                    job.persist();
                    auditService.log(ownerUserId, "JOB_STATUS_CHANGED", "job_transition_status");
                    return toDto(job);
                });
    }

    private void validateTransition(JobStatus current, JobStatus target) {
        if (current == target) {
            return;
        }
        boolean valid = switch (current) {
            case DRAFT -> target == JobStatus.PUBLISHED || target == JobStatus.CLOSED;
            case PUBLISHED -> target == JobStatus.PAUSED || target == JobStatus.FILLED || target == JobStatus.CLOSED;
            case PAUSED -> target == JobStatus.PUBLISHED || target == JobStatus.CLOSED;
            case FILLED, CLOSED -> false;
        };
        if (!valid) {
            throw new IllegalStateException("Invalid status transition from " + current + " to " + target);
        }
    }

    private void validatePayload(JobPostingPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("payload is required");
        }
        if (payload.title() == null || payload.title().isBlank()) {
            throw new IllegalArgumentException("title is required");
        }
        if (payload.description() == null || payload.description().isBlank()) {
            throw new IllegalArgumentException("description is required");
        }
    }

    private void applyPayload(JobPosting job, JobPostingPayload payload) {
        job.title = payload.title().trim();
        job.description = payload.description().trim();
        job.requirementsText = payload.requirementsText();
        job.skillsRequired = payload.skillsRequired();
        job.workMode = payload.workMode();
        job.employmentType = payload.employmentType();
        job.country = payload.country();
    }

    private JobPostingDto toDto(JobPosting job) {
        return new JobPostingDto(
                job.id,
                job.title,
                job.description,
                job.requirementsText,
                job.skillsRequired,
                job.workMode,
                job.employmentType,
                job.country,
                job.status,
                job.createdAt,
                job.updatedAt,
                job.publishedAt,
                job.closedAt
        );
    }

    private String jobToText(JobPosting job) {
        return Stream.of(
                        "Title: " + job.title,
                        "Description: " + job.description,
                        optionalField("Requirements", job.requirementsText),
                        job.skillsRequired == null || job.skillsRequired.isEmpty()
                                ? null
                                : "Required Skills: " + String.join(", ", job.skillsRequired),
                        job.workMode != null ? "Work Mode: " + job.workMode : null,
                        job.employmentType != null ? "Employment: " + job.employmentType : null,
                        optionalField("Country", job.country)
                )
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(". "));
    }

    private String optionalField(String label, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return label + ": " + value;
    }
}
