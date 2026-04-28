package org.peoplemesh.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;

import java.util.UUID;

public record AuthIdentityResponse(
        @JsonProperty("user_id") UUID userId,
        String provider,
        @Valid EntitlementsInfo entitlements,
        @JsonProperty("display_name") String displayName,
        @JsonProperty("photo_url") String photoUrl
) {

    public record EntitlementsInfo(
            @JsonProperty("is_admin") Boolean isAdmin
    ) {}
}
