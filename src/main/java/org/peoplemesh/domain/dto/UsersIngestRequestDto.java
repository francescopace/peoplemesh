package org.peoplemesh.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class UsersIngestRequestDto {
    @NotEmpty
    public List<@Valid UserIngestEntryDto> users;
}
