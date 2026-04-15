package org.peoplemesh.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.peoplemesh.domain.enums.NodeType;

import java.util.List;
import java.util.Map;

public record NodePayload(
        @NotNull @JsonProperty("node_type") NodeType nodeType,
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 5000) String description,
        List<String> tags,
        @JsonProperty("structured_data") Map<String, Object> structuredData,
        @Size(max = 10) String country
) {}
