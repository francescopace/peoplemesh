package org.peoplemesh.domain.dto;

import java.time.Instant;
import java.util.UUID;

public record ConnectionDto(
        UUID connectionId,
        UUID partnerProfileId,
        String sharedContact,
        Instant connectedAt
) {}
