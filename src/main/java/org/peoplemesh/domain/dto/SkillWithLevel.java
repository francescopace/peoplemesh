package org.peoplemesh.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SkillWithLevel(
        @NotBlank
        @Size(max = 80)
        String name,
        @Min(1)
        @Max(5)
        @JsonProperty("min_level") Integer minLevel
) {}
