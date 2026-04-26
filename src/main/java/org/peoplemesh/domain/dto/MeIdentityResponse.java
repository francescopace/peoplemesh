package org.peoplemesh.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;

import java.util.UUID;

public record MeIdentityResponse(
        @Valid IdentityInfo identity,
        @Valid SessionInfo session
) {
    public record IdentityInfo(
            @JsonProperty("display_name") String displayName,
            @JsonProperty("photo_url") String photoUrl
    ) {}

    public record SessionInfo(
            @JsonProperty("user_id") UUID userId,
            String provider,
            @JsonProperty("email_present") Boolean emailPresent,
            @JsonProperty("profile_id") UUID profileId,
            @Valid EntitlementsInfo entitlements
    ) {}

    public record EntitlementsInfo(
            @JsonProperty("is_admin") Boolean isAdmin
    ) {}
}
