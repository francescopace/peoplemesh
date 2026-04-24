package org.peoplemesh.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.config.AppConfig;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest2 {

    @Mock AppConfig appConfig;

    @InjectMocks
    NotificationService service;

    @BeforeEach
    void setUp() {
        AppConfig.NotificationConfig notifConfig = mock(AppConfig.NotificationConfig.class);
        lenient().when(appConfig.notification()).thenReturn(notifConfig);
        lenient().when(notifConfig.enabled()).thenReturn(true);
        lenient().when(notifConfig.dryRun()).thenReturn(true);
        lenient().when(notifConfig.subjectPrefix()).thenReturn("[Test]");
    }

    @Test
    void notifyAuditEvent_nullUserId_doesNothing() {
        service.notifyAuditEvent(null, "ACTION", "tool", null);
    }

    @Test
    void notifyAuditEvent_nullAction_doesNothing() {
        service.notifyAuditEvent(UUID.randomUUID(), null, "tool", null);
    }

    @Test
    void notifyAuditEvent_blankAction_doesNothing() {
        service.notifyAuditEvent(UUID.randomUUID(), "  ", "tool", null);
    }

    @Test
    void notifyAuditEvent_disabled_doesNothing() {
        when(appConfig.notification().enabled()).thenReturn(false);
        service.notifyAuditEvent(UUID.randomUUID(), "ACTION", "tool", null);
    }

    

    @Test
    void maskEmail_shortLocal_returnsMask() throws Exception {
        Method maskEmail = NotificationService.class.getDeclaredMethod("maskEmail", String.class);
        maskEmail.setAccessible(true);

        assertEquals("***", maskEmail.invoke(null, "a@b.com"));
        assertEquals("a***@example.com", maskEmail.invoke(null, "alice@example.com"));
    }

    @Test
    void buildBody_containsAllFields() throws Exception {
        Method buildBody = NotificationService.class.getDeclaredMethod(
                "buildBody", UUID.class, String.class, String.class, String.class);
        buildBody.setAccessible(true);

        UUID userId = UUID.randomUUID();
        String body = (String) buildBody.invoke(null, userId, "LOGIN", "auth_tool", "{\"x\":1}");

        assertTrue(body.contains("LOGIN"));
        assertTrue(body.contains("auth_tool"));
        assertTrue(body.contains(userId.toString()));
    }

    @Test
    void buildBody_nullToolAndMetadata_showsDash() throws Exception {
        Method buildBody = NotificationService.class.getDeclaredMethod(
                "buildBody", UUID.class, String.class, String.class, String.class);
        buildBody.setAccessible(true);

        String body = (String) buildBody.invoke(null, UUID.randomUUID(), "ACTION", null, null);

        assertTrue(body.contains("Tool: -"));
        assertTrue(body.contains("Metadata: -"));
    }
}
