package org.peoplemesh.domain.dto;

public record InfoResponse(
        String organizationName,
        String contactEmail,
        String dpoName,
        String dpoEmail,
        String dataLocation,
        String governingLaw,
        AuthProvidersDto authProviders
) {}
