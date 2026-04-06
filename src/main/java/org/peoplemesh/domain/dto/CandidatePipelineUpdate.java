package org.peoplemesh.domain.dto;

import org.peoplemesh.domain.enums.PipelineStage;

public record CandidatePipelineUpdate(
        PipelineStage stage,
        Boolean shortlisted,
        String notes
) {}
