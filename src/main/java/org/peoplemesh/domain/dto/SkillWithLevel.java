package org.peoplemesh.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SkillWithLevel(
        String name,
        @JsonProperty("min_level") Integer minLevel
) {}
