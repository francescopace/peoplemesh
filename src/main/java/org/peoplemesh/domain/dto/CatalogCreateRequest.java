package org.peoplemesh.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record CatalogCreateRequest(
        @NotBlank @Size(max = 200) String name,
        String description,
        @JsonProperty("level_scale") Map<String, Object> levelScale,
        @Size(max = 50) String source
) {}
