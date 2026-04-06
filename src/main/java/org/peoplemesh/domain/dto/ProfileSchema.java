package org.peoplemesh.domain.dto;

import org.peoplemesh.domain.enums.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public record ProfileSchema(
        @JsonProperty("profile_version") String profileVersion,
        @JsonProperty("generated_at") Instant generatedAt,
        @Valid ConsentInfo consent,
        @Valid ProfessionalInfo professional,
        @JsonProperty("interests_professional") @Valid InterestsInfo interestsProfessional,
        @Valid GeographyInfo geography,
        @Valid PrivacyInfo privacy
) {
    public record ConsentInfo(
            boolean explicit,
            Instant timestamp,
            @NotEmpty List<@NotNull String> scope,
            @JsonProperty("retention_days") int retentionDays,
            boolean revocable
    ) {}

    public record ProfessionalInfo(
            @Size(min = 1, max = 20) List<@NotNull @Size(max = 200) String> roles,
            Seniority seniority,
            @Size(max = 20) List<@NotNull @Size(max = 200) String> industries,
            @JsonProperty("skills_technical") @Size(max = 50) List<@NotNull @Size(max = 100) String> skillsTechnical,
            @JsonProperty("skills_soft") @Size(max = 30) List<@NotNull @Size(max = 100) String> skillsSoft,
            @JsonProperty("tools_and_tech") @Size(max = 50) List<@NotNull @Size(max = 100) String> toolsAndTech,
            @JsonProperty("languages_spoken") @Size(max = 30) List<@NotNull @Size(max = 50) String> languagesSpoken,
            @JsonProperty("work_mode_preference") WorkMode workModePreference,
            @JsonProperty("employment_type") EmploymentType employmentType
    ) {}

    public record InterestsInfo(
            @JsonProperty("topics_frequent") @Size(max = 30) List<@NotNull @Size(max = 200) String> topicsFrequent,
            @JsonProperty("learning_areas") @Size(max = 30) List<@NotNull @Size(max = 200) String> learningAreas,
            @JsonProperty("project_types") @Size(max = 20) List<@NotNull @Size(max = 200) String> projectTypes,
            @JsonProperty("collaboration_goals") @Size(max = 6) List<@NotNull CollaborationGoal> collaborationGoals
    ) {}

    public record GeographyInfo(
            @Size(max = 10) String country,
            @Size(max = 200) String city,
            @Size(max = 60) String timezone
    ) {}

    public record PrivacyInfo(
            @JsonProperty("show_city") boolean showCity,
            @JsonProperty("show_country") boolean showCountry,
            boolean searchable,
            @JsonProperty("contact_via") @Size(max = 50) String contactVia
    ) {}
}
