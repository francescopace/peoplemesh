package org.peoplemesh.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.util.HashUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AuditService creates {@code new AuditLogEntry()} internally and calls {@code persist()},
 * which requires Panache bytecode enhancement. Unit tests validate hashing logic and constants;
 * the full persist + notification flow is covered by FullFlowIT.
 */
@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    NotificationService notificationService;

    @InjectMocks
    AuditService auditService;

    @Test
    void hashUtils_sha256_isDeterministic() {
        UUID userId = UUID.randomUUID();
        String hash1 = HashUtils.sha256(userId.toString());
        String hash2 = HashUtils.sha256(userId.toString());
        assertEquals(hash1, hash2);
        assertNotEquals(userId.toString(), hash1);
    }

    @Test
    void hashUtils_sha256_producesHexString() {
        String hash = HashUtils.sha256("test");
        assertNotNull(hash);
        assertTrue(hash.matches("[0-9a-f]+"));
        assertEquals(64, hash.length());
    }

    @Test
    void auditService_isInjectable() {
        assertNotNull(auditService);
    }
}
