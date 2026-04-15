package org.peoplemesh.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SearchRequest(
        @NotBlank @Size(max = 500) String query,
        @Size(max = 10) String country
) {}
