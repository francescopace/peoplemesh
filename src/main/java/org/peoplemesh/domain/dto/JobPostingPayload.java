package org.peoplemesh.domain.dto;

import org.peoplemesh.domain.enums.EmploymentType;
import org.peoplemesh.domain.enums.WorkMode;

import java.util.List;

public record JobPostingPayload(
        String title,
        String description,
        String requirementsText,
        List<String> skillsRequired,
        WorkMode workMode,
        EmploymentType employmentType,
        String country
) {}
