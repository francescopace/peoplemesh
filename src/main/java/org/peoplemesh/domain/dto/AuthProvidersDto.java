package org.peoplemesh.domain.dto;

import java.util.List;

public record AuthProvidersDto(
        List<String> loginProviders,
        List<String> profileImportProviders
) {}
