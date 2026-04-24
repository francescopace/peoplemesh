package org.peoplemesh.domain.dto;

import java.time.Instant;
import java.util.List;

public record PrivacyDashboard(
        Instant lastProfileUpdate,
        boolean searchable,
        int activeConsents,
        List<String> consentScopes
) {}
