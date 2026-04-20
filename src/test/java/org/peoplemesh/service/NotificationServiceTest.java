package org.peoplemesh.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.domain.model.MeshNode;
import org.peoplemesh.repository.NodeRepository;
import org.peoplemesh.util.StringUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    AppConfig appConfig;

    @Mock
    AppConfig.NotificationConfig notificationConfig;

    @Mock
    NodeRepository nodeRepository;

    @InjectMocks
    NotificationService notificationService;

    private Method buildSubject;
    private Method maskEmail;

    @BeforeEach
    void setUp() throws Exception {
        buildSubject = NotificationService.class.getDeclaredMethod("buildSubject", String.class);
        buildSubject.setAccessible(true);
        maskEmail = NotificationService.class.getDeclaredMethod("maskEmail", String.class);
        maskEmail.setAccessible(true);
    }

    private String invokeBuildSubject(String action) throws Throwable {
        try {
            return (String) buildSubject.invoke(notificationService, action);
        } catch (InvocationTargetException e) {
            throw e.getCause() != null ? e.getCause() : e;
        }
    }

    private String invokeMaskEmail(String email) throws Throwable {
        try {
            return (String) maskEmail.invoke(null, email);
        } catch (InvocationTargetException e) {
            throw e.getCause() != null ? e.getCause() : e;
        }
    }

    @Test
    void humanize_underscoresReplaced() {
        String result = StringUtils.buildNotificationSubject(null, "JOB_CREATED");
        assertEquals("[PeopleMesh] job created", result);
    }

    @Test
    void maskEmail_normalEmail_masksLocalPart() throws Throwable {
        assertEquals("j***@example.com", invokeMaskEmail("john@example.com"));
    }

    @Test
    void maskEmail_shortLocal_returnsStars() throws Throwable {
        assertEquals("***", invokeMaskEmail("a@example.com"));
    }

    @Test
    void buildSubject_withPrefix_formatsCorrectly() throws Throwable {
        when(appConfig.notification()).thenReturn(notificationConfig);
        when(notificationConfig.subjectPrefix()).thenReturn("[PM]");

        String subject = invokeBuildSubject("PIPELINE_UPDATE");

        assertEquals("[PM] pipeline update", subject);
    }

    @Test
    void notifyAuditEvent_skipsWhenActionMissing() {
        notificationService.notifyAuditEvent(UUID.randomUUID(), "   ", "search", "{}");
        verifyNoInteractions(appConfig, nodeRepository);
    }

    @Test
    void notifyAuditEvent_skipsWhenNotificationsDisabled() {
        when(appConfig.notification()).thenReturn(notificationConfig);
        when(notificationConfig.enabled()).thenReturn(false);

        notificationService.notifyAuditEvent(UUID.randomUUID(), "SEARCH", "search", "{}");

        verify(nodeRepository, never()).findById(any());
    }

    @Test
    void notifyAuditEvent_skipsWhenNodeMissing() {
        UUID userId = UUID.randomUUID();
        when(appConfig.notification()).thenReturn(notificationConfig);
        when(notificationConfig.enabled()).thenReturn(true);
        when(nodeRepository.findById(userId)).thenReturn(Optional.empty());

        notificationService.notifyAuditEvent(userId, "SEARCH", "search", "{}");

        verify(nodeRepository).findById(userId);
        verify(notificationConfig, never()).dryRun();
    }

    @Test
    void notifyAuditEvent_skipsWhenEmailMissing() {
        UUID userId = UUID.randomUUID();
        MeshNode node = new MeshNode();
        node.id = userId;
        node.externalId = "  ";

        when(appConfig.notification()).thenReturn(notificationConfig);
        when(notificationConfig.enabled()).thenReturn(true);
        when(nodeRepository.findById(userId)).thenReturn(Optional.of(node));

        notificationService.notifyAuditEvent(userId, "SEARCH", "search", "{}");

        verify(notificationConfig, never()).dryRun();
    }

    @Test
    void notifyAuditEvent_dryRunFlowReadsSubjectPrefix() {
        UUID userId = UUID.randomUUID();
        MeshNode node = new MeshNode();
        node.id = userId;
        node.externalId = "john@example.com";

        when(appConfig.notification()).thenReturn(notificationConfig);
        when(notificationConfig.enabled()).thenReturn(true);
        when(notificationConfig.dryRun()).thenReturn(true);
        when(notificationConfig.subjectPrefix()).thenReturn("[PM]");
        when(nodeRepository.findById(userId)).thenReturn(Optional.of(node));

        notificationService.notifyAuditEvent(userId, "PROFILE_UPDATED", "profile", "{\"x\":1}");

        verify(notificationConfig).subjectPrefix();
    }

    @Test
    void notifyAuditEvent_nonDryRunStillBuildsSubject() {
        UUID userId = UUID.randomUUID();
        MeshNode node = new MeshNode();
        node.id = userId;
        node.externalId = "john@example.com";

        when(appConfig.notification()).thenReturn(notificationConfig);
        when(notificationConfig.enabled()).thenReturn(true);
        when(notificationConfig.dryRun()).thenReturn(false);
        when(notificationConfig.subjectPrefix()).thenReturn("[PeopleMesh]");
        when(nodeRepository.findById(userId)).thenReturn(Optional.of(node));

        notificationService.notifyAuditEvent(userId, "EVENT_CREATED", "events", "{}");

        verify(notificationConfig).dryRun();
        verify(notificationConfig).subjectPrefix();
    }
}
