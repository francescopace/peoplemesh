package org.peoplemesh.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.util.StringUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    AppConfig appConfig;

    @Mock
    AppConfig.NotificationConfig notificationConfig;

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
}
