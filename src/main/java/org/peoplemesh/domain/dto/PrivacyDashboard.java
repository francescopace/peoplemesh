package org.peoplemesh.domain.dto;

import java.time.Instant;

public record PrivacyDashboard(
        int profileViewsAnonymized,
        int connectionRequestsReceived,
        Instant lastProfileUpdate,
        boolean searchable,
        int activeConsents
) {}
