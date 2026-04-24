package org.peoplemesh.domain.dto;

public record OidcSubject(
        String subject,
        String fullName,
        String givenName,
        String familyName,
        String email,
        String headline,
        String industry,
        String locale,
        String picture,
        String hostedDomain
) {}
