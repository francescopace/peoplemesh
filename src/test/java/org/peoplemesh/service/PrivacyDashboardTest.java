package org.peoplemesh.service;

import org.peoplemesh.domain.dto.PrivacyDashboard;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates PrivacyDashboard record construction and edge cases.
 */
class PrivacyDashboardTest {

    @Test
    void constructsWithAllFields() {
        Instant now = Instant.now();
        PrivacyDashboard dashboard = new PrivacyDashboard(0, 5, now, true, 2);

        assertEquals(0, dashboard.profileViewsAnonymized());
        assertEquals(5, dashboard.connectionRequestsReceived());
        assertEquals(now, dashboard.lastProfileUpdate());
        assertTrue(dashboard.searchable());
        assertEquals(2, dashboard.activeConsents());
    }

    @Test
    void handlesNullLastUpdate() {
        PrivacyDashboard dashboard = new PrivacyDashboard(0, 0, null, false, 0);
        assertNull(dashboard.lastProfileUpdate());
        assertFalse(dashboard.searchable());
    }
}
