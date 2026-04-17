package org.peoplemesh.domain.dto;

import org.peoplemesh.domain.enums.EmploymentType;
import org.peoplemesh.domain.enums.Seniority;
import org.peoplemesh.domain.enums.WorkMode;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ProfileSchema(
        @JsonProperty("profile_version") String profileVersion,
        @JsonProperty("generated_at") Instant generatedAt,
        @Valid ConsentInfo consent,
        @Valid ProfessionalInfo professional,
        @JsonProperty("interests_professional") @Valid InterestsInfo interestsProfessional,
        @Valid PersonalInfo personal,
        @Valid GeographyInfo geography,
        @JsonProperty("field_provenance") Map<String, String> fieldProvenance,
        @Valid IdentityInfo identity
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
            @JsonProperty("employment_type") EmploymentType employmentType,
            @JsonProperty("slack_handle") @Size(max = 100) String slackHandle,
            @JsonProperty("telegram_handle") @Size(max = 100) String telegramHandle,
            @JsonProperty("mobile_phone") @Size(max = 32) String mobilePhone
    ) {}

    public record InterestsInfo(
            @JsonProperty("topics_frequent") @Size(max = 30) List<@NotNull @Size(max = 200) String> topicsFrequent,
            @JsonProperty("learning_areas") @Size(max = 30) List<@NotNull @Size(max = 200) String> learningAreas,
            @JsonProperty("project_types") @Size(max = 20) List<@NotNull @Size(max = 200) String> projectTypes
    ) {}

    public record PersonalInfo(
            @Size(max = 30) List<@NotNull @Size(max = 100) String> hobbies,
            @Size(max = 20) List<@NotNull @Size(max = 100) String> sports,
            @Size(max = 20) List<@NotNull @Size(max = 200) String> education,
            @Size(max = 20) List<@NotNull @Size(max = 200) String> causes,
            @JsonProperty("personality_tags") @Size(max = 20) List<@NotNull @Size(max = 100) String> personalityTags,
            @JsonProperty("music_genres") @Size(max = 20) List<@NotNull @Size(max = 100) String> musicGenres,
            @JsonProperty("book_genres") @Size(max = 20) List<@NotNull @Size(max = 100) String> bookGenres
    ) {}

    public record GeographyInfo(
            @Size(max = 10) String country,
            @Size(max = 200) String city,
            @Size(max = 60) String timezone
    ) {}

    public record IdentityInfo(
            @JsonProperty("display_name") @Size(max = 200) String displayName,
            @JsonProperty("first_name") @Size(max = 120) String firstName,
            @JsonProperty("last_name") @Size(max = 120) String lastName,
            @Size(max = 320) String email,
            @JsonProperty("photo_url") @Size(max = 2048) String photoUrl,
            @Size(max = 20) String locale,
            @Size(max = 200) String company
    ) {}
}
