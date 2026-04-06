package org.peoplemesh.domain.dto;

import org.peoplemesh.domain.enums.EmploymentType;
import org.peoplemesh.domain.enums.JobStatus;
import org.peoplemesh.domain.enums.WorkMode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record JobPostingDto(
        UUID id,
        String title,
        String description,
        String requirementsText,
        List<String> skillsRequired,
        WorkMode workMode,
        EmploymentType employmentType,
        String country,
        JobStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant publishedAt,
        Instant closedAt
) {}
