package org.peoplemesh.domain.dto;

import org.peoplemesh.domain.enums.PipelineStage;

import java.time.Instant;
import java.util.UUID;

public record CandidatePipelineDto(
        UUID entryId,
        UUID jobId,
        UUID candidateUserId,
        UUID candidateProfileId,
        PipelineStage stage,
        boolean shortlisted,
        String notes,
        Instant createdAt,
        Instant updatedAt,
        Instant lastStageAt
) {}
