package org.peoplemesh.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public class AtsJobEntryDto {
    @JsonProperty("external_id")
    @NotBlank
    public String externalId;

    @NotBlank
    public String title;

    @NotBlank
    public String description;

    @JsonProperty("requirements_text")
    public String requirementsText;

    @JsonProperty("skills_required")
    public List<String> skillsRequired;

    @JsonProperty("work_mode")
    public String workMode;

    @JsonProperty("employment_type")
    public String employmentType;

    public String country;
    public String status;

    @JsonProperty("external_url")
    public String externalUrl;
}
