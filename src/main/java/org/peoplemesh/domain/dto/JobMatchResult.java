package org.peoplemesh.domain.dto;

import org.peoplemesh.domain.enums.EmploymentType;
import org.peoplemesh.domain.enums.JobStatus;
import org.peoplemesh.domain.enums.WorkMode;

import java.util.List;
import java.util.UUID;

public record JobMatchResult(
        UUID jobId,
        String title,
        JobStatus status,
        WorkMode workMode,
        EmploymentType employmentType,
        String country,
        List<String> skillsRequired,
        double score,
        JobMatchBreakdown breakdown
) {}
