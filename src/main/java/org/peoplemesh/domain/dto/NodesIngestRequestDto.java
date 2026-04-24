package org.peoplemesh.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class NodesIngestRequestDto {
    @NotEmpty
    public List<@Valid NodeIngestEntryDto> nodes;
}
