package org.peoplemesh.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.domain.model.AuditLogEntry;
import org.peoplemesh.repository.AuditLogRepository;
import org.peoplemesh.util.HashUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    NotificationService notificationService;

    @Mock
    AuditLogRepository auditLogRepository;

    @InjectMocks
    AuditService auditService;

    @Test
    void sha256_whenInputMatches_returnsDeterministicDigest() {
        UUID userId = UUID.randomUUID();
        String hash1 = HashUtils.sha256(userId.toString());
        String hash2 = HashUtils.sha256(userId.toString());
        assertEquals(hash1, hash2);
        assertNotEquals(userId.toString(), hash1);
    }

    @Test
    void sha256_whenCalled_returns64CharLowercaseHexDigest() {
        String hash = HashUtils.sha256("test");
        assertNotNull(hash);
        assertTrue(hash.matches("[0-9a-f]+"));
        assertEquals(64, hash.length());
    }

    @Test
    void auditService_whenInjected_isNotNull() {
        assertNotNull(auditService);
    }

    @Test
    void log_persistsHashedAuditEntryAndNotifies() {
        UUID userId = UUID.randomUUID();
        String ip = "10.0.0.42";

        auditService.log(userId, "SEARCH", "search-tool", ip, "{\"q\":\"java\"}");

        var captor = org.mockito.ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(auditLogRepository).persist(captor.capture());
        AuditLogEntry entry = captor.getValue();
        assertEquals(HashUtils.sha256(userId.toString()), entry.userIdHash);
        assertEquals(HashUtils.sha256(ip), entry.ipHash);
        assertEquals("SEARCH", entry.action);
        assertEquals("search-tool", entry.toolName);
        assertEquals("{\"q\":\"java\"}", entry.metadataJson);
        assertNotNull(entry.timestamp);

        verify(notificationService).notifyAuditEvent(userId, "SEARCH", "search-tool", "{\"q\":\"java\"}");
    }

    @Test
    void log_handlesNotificationFailureWithoutFailingAuditWrite() {
        UUID userId = UUID.randomUUID();
        doThrow(new RuntimeException("SMTP unavailable"))
                .when(notificationService).notifyAuditEvent(any(), any(), any(), any());

        assertDoesNotThrow(() -> auditService.log(userId, "EVENT_CREATED", "events", null, "{}"));
        verify(auditLogRepository).persist(any(AuditLogEntry.class));
    }

    @Test
    void log_overloadWithoutIp_persistsAuditAndNotifiesWithNullMetadata() {
        UUID userId = UUID.randomUUID();

        auditService.log(userId, "MATCH_REQUESTED", "matches");

        verify(auditLogRepository).persist(any(AuditLogEntry.class));
        verify(notificationService).notifyAuditEvent(userId, "MATCH_REQUESTED", "matches", null);
    }
}
