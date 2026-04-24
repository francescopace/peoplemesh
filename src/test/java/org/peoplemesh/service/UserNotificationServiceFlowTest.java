package org.peoplemesh.service;

import org.junit.jupiter.api.Test;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.domain.dto.UserNotificationDto;
import org.peoplemesh.domain.model.AuditLogEntry;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserNotificationServiceFlowTest {

    @Test
    void getRecentNotifications_mapsEntriesAndUsesNormalizedLimit() {
        TestableUserNotificationService service = new TestableUserNotificationService();
        service.appConfig = mock(AppConfig.class);
        AppConfig.NotificationConfig notificationConfig = mock(AppConfig.NotificationConfig.class);
        when(service.appConfig.notification()).thenReturn(notificationConfig);
        when(notificationConfig.subjectPrefix()).thenReturn("PM");

        AuditLogEntry entry = new AuditLogEntry();
        entry.id = UUID.randomUUID();
        entry.action = "USER_SIGNED_IN";
        entry.toolName = "login";
        entry.timestamp = Instant.parse("2026-01-01T00:00:00Z");
        entry.metadataJson = "ok";
        service.entries = List.of(entry);

        UUID userId = UUID.randomUUID();
        List<UserNotificationDto> out = service.getRecentNotifications(userId, 500);

        assertEquals(1, out.size());
        assertEquals(entry.id, out.getFirst().id());
        assertEquals("PM user signed in", out.getFirst().subject());
        assertEquals("USER_SIGNED_IN", out.getFirst().action());
        assertEquals("login", out.getFirst().toolName());
        assertEquals("ok", out.getFirst().metadata());
        assertEquals(100, service.lastPageSize);
        assertEquals("fixed-hash", service.lastUserHash);
    }

    @Test
    void getRecentNotifications_truncatesLongMetadata() {
        TestableUserNotificationService service = new TestableUserNotificationService();
        service.appConfig = mock(AppConfig.class);
        AppConfig.NotificationConfig notificationConfig = mock(AppConfig.NotificationConfig.class);
        when(service.appConfig.notification()).thenReturn(notificationConfig);
        when(notificationConfig.subjectPrefix()).thenReturn("[PeopleMesh]");

        AuditLogEntry entry = new AuditLogEntry();
        entry.id = UUID.randomUUID();
        entry.action = "MATCH_FOUND";
        entry.toolName = "matcher";
        entry.timestamp = Instant.now();
        entry.metadataJson = "x".repeat(260);
        service.entries = List.of(entry);

        List<UserNotificationDto> out = service.getRecentNotifications(UUID.randomUUID(), null);

        assertEquals(1, out.size());
        assertNotNull(out.getFirst().metadata());
        assertTrue(out.getFirst().metadata().endsWith("...[truncated]"));
        assertEquals(20, service.lastPageSize);
    }

    private static final class TestableUserNotificationService extends UserNotificationService {
        List<AuditLogEntry> entries = List.of();
        String lastUserHash;
        int lastPageSize;

        @Override
        List<AuditLogEntry> fetchRecentEntries(String userHash, int pageSize) {
            this.lastUserHash = userHash;
            this.lastPageSize = pageSize;
            return entries;
        }

        @Override
        String hashUserId(UUID userId) {
            return "fixed-hash";
        }
    }
}
