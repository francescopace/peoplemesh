package org.peoplemesh.domain.dto;

import java.util.List;

public record AuthProvidersDto(
        List<String> providers,
        List<String> configured
) {}
