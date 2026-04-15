package org.peoplemesh.domain.dto;

public record LdapUserPreview(
        String uid, String displayName, String email,
        String firstName, String lastName, String title,
        String department, String country, String city
) {}
