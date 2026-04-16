package org.peoplemesh.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public class AtsJobEntryDto {
    @JsonProperty("external_id")
    @NotBlank
    @Size(max = 200)
    public String externalId;

    @NotBlank
    @Size(max = 200)
    public String title;

    @NotBlank
    @Size(max = 10000)
    public String description;

    @JsonProperty("requirements_text")
    @Size(max = 10000)
    public String requirementsText;

    @JsonProperty("skills_required")
    @Size(max = 100)
    public List<@NotBlank @Size(max = 100) String> skillsRequired;

    @JsonProperty("work_mode")
    @Pattern(
            regexp = "(?i)^(REMOTE|HYBRID|ONSITE|ON_SITE|FLEXIBLE)$",
            message = "work_mode must be REMOTE, HYBRID, ONSITE, ON_SITE, or FLEXIBLE"
    )
    public String workMode;

    @JsonProperty("employment_type")
    @Pattern(
            regexp = "(?i)^(EMPLOYED|CONTRACT|FREELANCE|INTERNSHIP|PART_TIME|TEMPORARY)$",
            message = "employment_type is invalid"
    )
    public String employmentType;

    @Size(max = 10)
    public String country;

    @Pattern(
            regexp = "(?i)^(PUBLISHED|DRAFT|FILLED|HIRED|CLOSED|ARCHIVED|CANCELLED|DELETED)$",
            message = "status is invalid"
    )
    public String status;

    @JsonProperty("external_url")
    @Size(max = 1000)
    public String externalUrl;
}
