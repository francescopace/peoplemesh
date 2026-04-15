package org.peoplemesh.service;

import org.peoplemesh.domain.dto.PrivacyDashboard;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates PrivacyDashboard record construction and edge cases.
 */
class PrivacyDashboardTest {

    @Test
    void constructsWithAllFields() {
        Instant now = Instant.now();
        PrivacyDashboard dashboard = new PrivacyDashboard(now, true, 2,
                List.of("professional_matching", "embedding_processing"));

        assertEquals(now, dashboard.lastProfileUpdate());
        assertTrue(dashboard.searchable());
        assertEquals(2, dashboard.activeConsents());
        assertEquals(List.of("professional_matching", "embedding_processing"), dashboard.consentScopes());
    }

    @Test
    void handlesNullLastUpdate() {
        PrivacyDashboard dashboard = new PrivacyDashboard(null, false, 0, List.of());
        assertNull(dashboard.lastProfileUpdate());
        assertFalse(dashboard.searchable());
        assertTrue(dashboard.consentScopes().isEmpty());
    }
}
