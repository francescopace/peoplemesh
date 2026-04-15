package org.peoplemesh.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.config.AppConfig;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserNotificationServiceTest {

    @Mock
    AppConfig appConfig;

    @InjectMocks
    UserNotificationService service;

    @Test
    void normalizeLimit_null_returnsDefault() throws Exception {
        assertEquals(20, invokeNormalizeLimit(null));
    }

    @Test
    void normalizeLimit_negative_returnsDefault() throws Exception {
        assertEquals(20, invokeNormalizeLimit(-1));
    }

    @Test
    void normalizeLimit_zero_returnsDefault() throws Exception {
        assertEquals(20, invokeNormalizeLimit(0));
    }

    @Test
    void normalizeLimit_validSmall_returnsValue() throws Exception {
        assertEquals(15, invokeNormalizeLimit(15));
    }

    @Test
    void normalizeLimit_exceedsMax_returnsMax() throws Exception {
        assertEquals(100, invokeNormalizeLimit(500));
    }

    @Test
    void buildSubject_normalAction_formatsCorrectly() throws Exception {
        AppConfig.NotificationConfig nc = mock(AppConfig.NotificationConfig.class);
        when(appConfig.notification()).thenReturn(nc);
        when(nc.subjectPrefix()).thenReturn("PM");
        assertEquals("PM user signed in", invokeBuildSubject("USER_SIGNED_IN"));
    }

    @Test
    void buildSubject_nullAction_returnsDefaultEvent() throws Exception {
        AppConfig.NotificationConfig nc = mock(AppConfig.NotificationConfig.class);
        when(appConfig.notification()).thenReturn(nc);
        when(nc.subjectPrefix()).thenReturn("[PeopleMesh]");
        assertEquals("[PeopleMesh] event", invokeBuildSubject(null));
    }

    @Test
    void buildSubject_blankPrefix_usesDefault() throws Exception {
        AppConfig.NotificationConfig nc = mock(AppConfig.NotificationConfig.class);
        when(appConfig.notification()).thenReturn(nc);
        when(nc.subjectPrefix()).thenReturn("   ");
        assertEquals("[PeopleMesh] hello world", invokeBuildSubject("HELLO_WORLD"));
    }

    private int invokeNormalizeLimit(Integer limit) throws Exception {
        Method m = UserNotificationService.class.getDeclaredMethod("normalizeLimit", Integer.class);
        m.setAccessible(true);
        return (int) m.invoke(service, limit);
    }

    private String invokeBuildSubject(String action) throws Exception {
        Method m = UserNotificationService.class.getDeclaredMethod("buildSubject", String.class);
        m.setAccessible(true);
        return (String) m.invoke(service, action);
    }
}
