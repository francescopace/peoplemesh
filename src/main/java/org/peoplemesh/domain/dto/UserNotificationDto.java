package org.peoplemesh.domain.dto;

import java.time.Instant;
import java.util.UUID;

public record UserNotificationDto(
        UUID id,
        String subject,
        String action,
        String toolName,
        String metadata,
        Instant timestamp
) {}
