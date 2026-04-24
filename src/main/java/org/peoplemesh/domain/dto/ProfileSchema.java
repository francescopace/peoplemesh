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
        @Valid ContactsInfo contacts,
        @JsonProperty("interests_professional") @Valid InterestsInfo interestsProfessional,
        @Valid PersonalInfo personal,
        @Valid GeographyInfo geography,
        @JsonProperty("field_provenance") Map<String, String> fieldProvenance,
        @Valid IdentityInfo identity
) {
    public static final int MAX_ROLES = 20;
    public static final int MAX_ROLE_LENGTH = 200;
    public static final int MAX_INDUSTRIES = 20;
    public static final int MAX_INDUSTRY_LENGTH = 200;
    public static final int MAX_SKILLS_TECHNICAL = 50;
    public static final int MAX_SKILL_TECHNICAL_LENGTH = 100;
    public static final int MAX_SKILLS_SOFT = 30;
    public static final int MAX_SKILL_SOFT_LENGTH = 100;
    public static final int MAX_TOOLS_AND_TECH = 50;
    public static final int MAX_TOOL_AND_TECH_LENGTH = 100;
    public static final int MAX_LANGUAGES_SPOKEN = 30;
    public static final int MAX_LANGUAGE_SPOKEN_LENGTH = 50;
    public static final int MAX_LEARNING_AREAS = 50;
    public static final int MAX_LEARNING_AREA_LENGTH = 200;
    public static final int MAX_PROJECT_TYPES = 20;
    public static final int MAX_PROJECT_TYPE_LENGTH = 200;
    public static final int MAX_HOBBIES = 30;
    public static final int MAX_HOBBY_LENGTH = 100;
    public static final int MAX_SPORTS = 20;
    public static final int MAX_SPORT_LENGTH = 100;
    public static final int MAX_EDUCATION = 20;
    public static final int MAX_EDUCATION_LENGTH = 200;
    public static final int MAX_CAUSES = 20;
    public static final int MAX_CAUSE_LENGTH = 200;
    public static final int MAX_PERSONALITY_TAGS = 20;
    public static final int MAX_PERSONALITY_TAG_LENGTH = 100;
    public static final int MAX_MUSIC_GENRES = 20;
    public static final int MAX_MUSIC_GENRE_LENGTH = 100;
    public static final int MAX_BOOK_GENRES = 20;
    public static final int MAX_BOOK_GENRE_LENGTH = 100;
    public static final int MAX_COUNTRY_LENGTH = 10;
    public static final int MAX_CITY_LENGTH = 200;
    public static final int MAX_TIMEZONE_LENGTH = 60;
    public static final int MAX_SLACK_HANDLE_LENGTH = 100;
    public static final int MAX_TELEGRAM_HANDLE_LENGTH = 100;
    public static final int MAX_MOBILE_PHONE_LENGTH = 32;
    public static final int MAX_LINKEDIN_URL_LENGTH = 2048;
    public static final int MAX_IDENTITY_DISPLAY_NAME_LENGTH = 200;
    public static final int MAX_IDENTITY_FIRST_NAME_LENGTH = 120;
    public static final int MAX_IDENTITY_LAST_NAME_LENGTH = 120;
    public static final int MAX_IDENTITY_EMAIL_LENGTH = 320;
    public static final int MAX_IDENTITY_PHOTO_URL_LENGTH = 2048;
    public static final int MAX_IDENTITY_COMPANY_LENGTH = 200;
    public static final int MAX_IDENTITY_BIRTH_DATE_LENGTH = 32;

    public record ConsentInfo(
            boolean explicit,
            Instant timestamp,
            @NotEmpty List<@NotNull String> scope,
            @JsonProperty("retention_days") int retentionDays,
            boolean revocable
    ) {}

    public record ProfessionalInfo(
            @Size(min = 1, max = MAX_ROLES) List<@NotNull @Size(max = MAX_ROLE_LENGTH) String> roles,
            Seniority seniority,
            @Size(max = MAX_INDUSTRIES) List<@NotNull @Size(max = MAX_INDUSTRY_LENGTH) String> industries,
            @JsonProperty("skills_technical") @Size(max = MAX_SKILLS_TECHNICAL) List<@NotNull @Size(max = MAX_SKILL_TECHNICAL_LENGTH) String> skillsTechnical,
            @JsonProperty("skills_soft") @Size(max = MAX_SKILLS_SOFT) List<@NotNull @Size(max = MAX_SKILL_SOFT_LENGTH) String> skillsSoft,
            @JsonProperty("tools_and_tech") @Size(max = MAX_TOOLS_AND_TECH) List<@NotNull @Size(max = MAX_TOOL_AND_TECH_LENGTH) String> toolsAndTech,
            @JsonProperty("languages_spoken") @Size(max = MAX_LANGUAGES_SPOKEN) List<@NotNull @Size(max = MAX_LANGUAGE_SPOKEN_LENGTH) String> languagesSpoken,
            @JsonProperty("work_mode_preference") WorkMode workModePreference,
            @JsonProperty("employment_type") EmploymentType employmentType
    ) {}

    public record ContactsInfo(
            @JsonProperty("slack_handle") @Size(max = MAX_SLACK_HANDLE_LENGTH) String slackHandle,
            @JsonProperty("telegram_handle") @Size(max = MAX_TELEGRAM_HANDLE_LENGTH) String telegramHandle,
            @JsonProperty("mobile_phone") @Size(max = MAX_MOBILE_PHONE_LENGTH) String mobilePhone,
            @JsonProperty("linkedin_url") @Size(max = MAX_LINKEDIN_URL_LENGTH) String linkedinUrl
    ) {}

    public record InterestsInfo(
            @JsonProperty("learning_areas") @Size(max = MAX_LEARNING_AREAS) List<@NotNull @Size(max = MAX_LEARNING_AREA_LENGTH) String> learningAreas,
            @JsonProperty("project_types") @Size(max = MAX_PROJECT_TYPES) List<@NotNull @Size(max = MAX_PROJECT_TYPE_LENGTH) String> projectTypes
    ) {}

    public record PersonalInfo(
            @Size(max = MAX_HOBBIES) List<@NotNull @Size(max = MAX_HOBBY_LENGTH) String> hobbies,
            @Size(max = MAX_SPORTS) List<@NotNull @Size(max = MAX_SPORT_LENGTH) String> sports,
            @Size(max = MAX_EDUCATION) List<@NotNull @Size(max = MAX_EDUCATION_LENGTH) String> education,
            @Size(max = MAX_CAUSES) List<@NotNull @Size(max = MAX_CAUSE_LENGTH) String> causes,
            @JsonProperty("personality_tags") @Size(max = MAX_PERSONALITY_TAGS) List<@NotNull @Size(max = MAX_PERSONALITY_TAG_LENGTH) String> personalityTags,
            @JsonProperty("music_genres") @Size(max = MAX_MUSIC_GENRES) List<@NotNull @Size(max = MAX_MUSIC_GENRE_LENGTH) String> musicGenres,
            @JsonProperty("book_genres") @Size(max = MAX_BOOK_GENRES) List<@NotNull @Size(max = MAX_BOOK_GENRE_LENGTH) String> bookGenres
    ) {}

    public record GeographyInfo(
            @Size(max = MAX_COUNTRY_LENGTH) String country,
            @Size(max = MAX_CITY_LENGTH) String city,
            @Size(max = MAX_TIMEZONE_LENGTH) String timezone
    ) {}

    public record IdentityInfo(
            @JsonProperty("display_name") @Size(max = MAX_IDENTITY_DISPLAY_NAME_LENGTH) String displayName,
            @JsonProperty("first_name") @Size(max = MAX_IDENTITY_FIRST_NAME_LENGTH) String firstName,
            @JsonProperty("last_name") @Size(max = MAX_IDENTITY_LAST_NAME_LENGTH) String lastName,
            @Size(max = MAX_IDENTITY_EMAIL_LENGTH) String email,
            @JsonProperty("photo_url") @Size(max = MAX_IDENTITY_PHOTO_URL_LENGTH) String photoUrl,
            @Size(max = MAX_IDENTITY_COMPANY_LENGTH) String company,
            @JsonProperty("birth_date") @Size(max = MAX_IDENTITY_BIRTH_DATE_LENGTH) String birthDate
    ) {}
}
