package org.peoplemesh.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class UserIngestEntryDto {
    @JsonProperty("node_id")
    public UUID nodeId;

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

    @Size(max = 10)
    public String country;

    @Size(max = 100)
    public List<@NotBlank @Size(max = 100) String> tags;

    @JsonProperty("structured_data")
    public Map<String, Object> structuredData;

    public Boolean searchable;
}
