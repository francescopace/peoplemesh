package org.peoplemesh.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public class AtsIngestRequestDto {
    @JsonProperty("owner_user_id")
    @NotNull
    public UUID ownerUserId;

    @NotEmpty
    public List<@Valid AtsJobEntryDto> jobs;
}
