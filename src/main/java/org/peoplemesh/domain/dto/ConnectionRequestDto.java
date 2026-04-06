package org.peoplemesh.domain.dto;

import org.peoplemesh.domain.enums.ConnectionStatus;

import java.time.Instant;
import java.util.UUID;

public record ConnectionRequestDto(
        UUID requestId,
        UUID fromProfileId,
        String message,
        ConnectionStatus status,
        Instant createdAt
) {}
