package org.peoplemesh.security;

import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.core.HttpHeaders;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.config.AppConfig;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaintenanceAccessGuardTest {

    @Mock AppConfig config;
    @Mock AppConfig.MaintenanceConfig maintenanceConfig;
    @Mock HttpHeaders httpHeaders;

    @Test
    void assertAuthorized_noApiKeyConfigured_throwsForbidden() {
        when(config.maintenance()).thenReturn(maintenanceConfig);
        when(maintenanceConfig.apiKey()).thenReturn(Optional.empty());

        assertThrows(ForbiddenException.class,
                () -> MaintenanceAccessGuard.assertAuthorized("some-key", config, httpHeaders));
    }

    @Test
    void assertAuthorized_blankApiKeyConfigured_throwsForbidden() {
        when(config.maintenance()).thenReturn(maintenanceConfig);
        when(maintenanceConfig.apiKey()).thenReturn(Optional.of("  "));

        assertThrows(ForbiddenException.class,
                () -> MaintenanceAccessGuard.assertAuthorized("some-key", config, httpHeaders));
    }

    @Test
    void assertAuthorized_wrongKey_throwsForbidden() {
        when(config.maintenance()).thenReturn(maintenanceConfig);
        when(maintenanceConfig.apiKey()).thenReturn(Optional.of("correct-key"));

        assertThrows(ForbiddenException.class,
                () -> MaintenanceAccessGuard.assertAuthorized("wrong-key", config, httpHeaders));
    }

    @Test
    void assertAuthorized_nullKey_throwsForbidden() {
        when(config.maintenance()).thenReturn(maintenanceConfig);
        when(maintenanceConfig.apiKey()).thenReturn(Optional.of("correct-key"));

        assertThrows(ForbiddenException.class,
                () -> MaintenanceAccessGuard.assertAuthorized(null, config, httpHeaders));
    }

    @Test
    void assertAuthorized_correctKey_noCidrs_succeeds() {
        when(config.maintenance()).thenReturn(maintenanceConfig);
        when(maintenanceConfig.apiKey()).thenReturn(Optional.of("correct-key"));
        when(maintenanceConfig.allowedCidrs()).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> MaintenanceAccessGuard.assertAuthorized("correct-key", config, httpHeaders));
    }

    @Test
    void assertAuthorized_correctKey_blankCidrs_succeeds() {
        when(config.maintenance()).thenReturn(maintenanceConfig);
        when(maintenanceConfig.apiKey()).thenReturn(Optional.of("correct-key"));
        when(maintenanceConfig.allowedCidrs()).thenReturn(Optional.of("  "));

        assertDoesNotThrow(() -> MaintenanceAccessGuard.assertAuthorized("correct-key", config, httpHeaders));
    }

    @Test
    void assertAuthorized_correctKey_allowedIp_succeeds() {
        when(config.maintenance()).thenReturn(maintenanceConfig);
        when(maintenanceConfig.apiKey()).thenReturn(Optional.of("key"));
        when(maintenanceConfig.allowedCidrs()).thenReturn(Optional.of("10.0.0.0/24"));
        when(httpHeaders.getHeaderString("X-Forwarded-For")).thenReturn("10.0.0.5");

        assertDoesNotThrow(() -> MaintenanceAccessGuard.assertAuthorized("key", config, httpHeaders));
    }

    @Test
    void assertAuthorized_correctKey_blockedIp_throwsForbidden() {
        when(config.maintenance()).thenReturn(maintenanceConfig);
        when(maintenanceConfig.apiKey()).thenReturn(Optional.of("key"));
        when(maintenanceConfig.allowedCidrs()).thenReturn(Optional.of("10.0.0.0/24"));
        when(httpHeaders.getHeaderString("X-Forwarded-For")).thenReturn("192.168.1.1");

        assertThrows(ForbiddenException.class,
                () -> MaintenanceAccessGuard.assertAuthorized("key", config, httpHeaders));
    }

    @Test
    void assertAuthorized_correctKey_noForwardedFor_throwsForbidden() {
        when(config.maintenance()).thenReturn(maintenanceConfig);
        when(maintenanceConfig.apiKey()).thenReturn(Optional.of("key"));
        when(maintenanceConfig.allowedCidrs()).thenReturn(Optional.of("10.0.0.0/24"));
        when(httpHeaders.getHeaderString("X-Forwarded-For")).thenReturn(null);

        assertThrows(ForbiddenException.class,
                () -> MaintenanceAccessGuard.assertAuthorized("key", config, httpHeaders));
    }

    @Test
    void assertAuthorized_multipleForwardedIps_usesFirst() {
        when(config.maintenance()).thenReturn(maintenanceConfig);
        when(maintenanceConfig.apiKey()).thenReturn(Optional.of("key"));
        when(maintenanceConfig.allowedCidrs()).thenReturn(Optional.of("10.0.0.0/24"));
        when(httpHeaders.getHeaderString("X-Forwarded-For")).thenReturn("10.0.0.5, 192.168.1.1");

        assertDoesNotThrow(() -> MaintenanceAccessGuard.assertAuthorized("key", config, httpHeaders));
    }
}
