package org.peoplemesh.api.resource;

import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.domain.dto.LdapImportResult;
import org.peoplemesh.domain.dto.LdapUserPreview;
import org.peoplemesh.domain.exception.BusinessException;
import org.peoplemesh.domain.exception.NotFoundBusinessException;
import org.peoplemesh.domain.exception.ValidationBusinessException;
import org.peoplemesh.service.MaintenanceService;
import org.peoplemesh.service.NodeEmbeddingMaintenanceService;
import org.peoplemesh.util.IpAllowlistUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaintenanceResourceTest {

    @Mock
    AppConfig config;
    @Mock
    AppConfig.MaintenanceConfig maintenanceConfig;
    @Mock
    MaintenanceService maintenanceService;
    @Mock
    HttpHeaders httpHeaders;

    @InjectMocks
    MaintenanceResource resource;

    @Test
    void matchesAnyCidr_exactIpMatch() {
        assertTrue(IpAllowlistUtils.matchesAnyCidr("10.0.0.1", List.of("10.0.0.1")));
    }

    @Test
    void matchesAnyCidr_noMatch() {
        assertFalse(IpAllowlistUtils.matchesAnyCidr("10.0.0.2", List.of("10.0.0.1")));
    }

    @Test
    void matchesAnyCidr_cidrMatch() {
        assertTrue(IpAllowlistUtils.matchesAnyCidr("192.168.1.50", List.of("192.168.1.0/24")));
    }

    @Test
    void matchesAnyCidr_cidrNoMatch() {
        assertFalse(IpAllowlistUtils.matchesAnyCidr("192.168.2.1", List.of("192.168.1.0/24")));
    }

    @Test
    void matchesAnyCidr_multipleCidrs() {
        List<String> cidrs = List.of("10.0.0.0/8", "192.168.1.0/24");
        assertTrue(IpAllowlistUtils.matchesAnyCidr("10.1.2.3", cidrs));
        assertTrue(IpAllowlistUtils.matchesAnyCidr("192.168.1.100", cidrs));
        assertFalse(IpAllowlistUtils.matchesAnyCidr("172.16.0.1", cidrs));
    }

    @Test
    void matchesAnyCidr_smallPrefix() {
        assertTrue(IpAllowlistUtils.matchesAnyCidr("10.255.255.255", List.of("10.0.0.0/8")));
    }

    @Test
    void matchesAnyCidr_slash32_exactMatch() {
        assertTrue(IpAllowlistUtils.matchesAnyCidr("192.168.1.1", List.of("192.168.1.1/32")));
        assertFalse(IpAllowlistUtils.matchesAnyCidr("192.168.1.2", List.of("192.168.1.1/32")));
    }

    @Test
    void matchesAnyCidr_invalidIp_returnsFalse() {
        assertFalse(IpAllowlistUtils.matchesAnyCidr("not-an-ip", List.of("10.0.0.0/8")));
    }

    @Test
    void matchesAnyCidr_emptyList_returnsFalse() {
        assertFalse(IpAllowlistUtils.matchesAnyCidr("10.0.0.1", List.of()));
    }

    @Test
    void ldapPreview_validKey_returnsResult() {
        when(config.maintenance()).thenReturn(maintenanceConfig);
        when(maintenanceConfig.apiKey()).thenReturn(Optional.of("valid-key"));
        when(maintenanceConfig.allowedCidrs()).thenReturn(Optional.empty());
        List<LdapUserPreview> previews = List.of(new LdapUserPreview("uid1", "User One", "u1@test.com",
                "User", "One", "Dev", "IT", "US", "NYC"));
        when(maintenanceService.previewLdapUsers(500)).thenReturn(previews);

        Response response = resource.ldapPreview("valid-key", 500);

        assertEquals(200, response.getStatus());
        assertSame(previews, response.getEntity());
        verify(maintenanceService).previewLdapUsers(500);
    }

    @Test
    void ldapImport_validKey_returnsResult() {
        when(config.maintenance()).thenReturn(maintenanceConfig);
        when(maintenanceConfig.apiKey()).thenReturn(Optional.of("valid-key"));
        when(maintenanceConfig.allowedCidrs()).thenReturn(Optional.empty());
        LdapImportResult result = new LdapImportResult(5, 2, 1, 0, 1000, List.of());
        when(maintenanceService.importFromLdap()).thenReturn(result);

        Response response = resource.ldapImport("valid-key");

        assertEquals(200, response.getStatus());
        assertSame(result, response.getEntity());
        verify(maintenanceService).importFromLdap();
    }

    @Test
    void ldapImport_invalidKey_throwsForbidden() {
        when(config.maintenance()).thenReturn(maintenanceConfig);
        when(maintenanceConfig.apiKey()).thenReturn(Optional.of("valid-key"));

        assertThrows(ForbiddenException.class, () -> resource.ldapImport("wrong-key"));
        verify(maintenanceService, never()).importFromLdap();
    }

    @Test
    void ldapImport_configError_returns400() {
        when(config.maintenance()).thenReturn(maintenanceConfig);
        when(maintenanceConfig.apiKey()).thenReturn(Optional.of("valid-key"));
        when(maintenanceConfig.allowedCidrs()).thenReturn(Optional.empty());
        when(maintenanceService.importFromLdap())
                .thenThrow(new ValidationBusinessException("LDAP configuration is invalid"));

        BusinessException ex = assertThrows(BusinessException.class, () -> resource.ldapImport("valid-key"));
        assertEquals(400, ex.status());
        assertEquals("Configuration Error", ex.title());
    }

    @Test
    void regenerateEmbeddings_withoutNodeType_regeneratesAll() {
        when(config.maintenance()).thenReturn(maintenanceConfig);
        when(maintenanceConfig.apiKey()).thenReturn(Optional.of("valid-key"));
        when(maintenanceConfig.allowedCidrs()).thenReturn(Optional.empty());
        NodeEmbeddingMaintenanceService.EmbeddingRegenerationJobStatus queuedStatus =
                new NodeEmbeddingMaintenanceService.EmbeddingRegenerationJobStatus(
                        "regenerate-embeddings",
                        UUID.randomUUID(),
                        NodeEmbeddingMaintenanceService.JobState.QUEUED,
                        "ALL",
                        false,
                        1,
                        12,
                        0,
                        0,
                        0,
                        null,
                        Instant.now(),
                        null,
                        null
                );
        when(maintenanceService.startEmbeddingRegeneration(null, false, 1))
                .thenReturn(queuedStatus);

        Response response = resource.regenerateEmbeddings("valid-key", null, false, 1);

        assertEquals(202, response.getStatus());
        assertSame(queuedStatus, response.getEntity());
        verify(maintenanceService).startEmbeddingRegeneration(null, false, 1);
    }

    @Test
    void regenerateEmbeddings_withNodeType_usesFilter() {
        when(config.maintenance()).thenReturn(maintenanceConfig);
        when(maintenanceConfig.apiKey()).thenReturn(Optional.of("valid-key"));
        when(maintenanceConfig.allowedCidrs()).thenReturn(Optional.empty());
        NodeEmbeddingMaintenanceService.EmbeddingRegenerationJobStatus queuedStatus =
                new NodeEmbeddingMaintenanceService.EmbeddingRegenerationJobStatus(
                        "regenerate-embeddings",
                        UUID.randomUUID(),
                        NodeEmbeddingMaintenanceService.JobState.QUEUED,
                        "JOB",
                        true,
                        32,
                        5,
                        0,
                        0,
                        0,
                        null,
                        Instant.now(),
                        null,
                        null
                );
        when(maintenanceService.startEmbeddingRegeneration("job", true, 32))
                .thenReturn(queuedStatus);

        Response response = resource.regenerateEmbeddings("valid-key", "job", true, 32);

        assertEquals(202, response.getStatus());
        assertSame(queuedStatus, response.getEntity());
        verify(maintenanceService).startEmbeddingRegeneration("job", true, 32);
    }

    @Test
    void regenerateEmbeddings_invalidNodeType_returns400() {
        when(config.maintenance()).thenReturn(maintenanceConfig);
        when(maintenanceConfig.apiKey()).thenReturn(Optional.of("valid-key"));
        when(maintenanceConfig.allowedCidrs()).thenReturn(Optional.empty());
        when(maintenanceService.startEmbeddingRegeneration("not-valid", false, 1))
                .thenThrow(new ValidationBusinessException("Invalid nodeType"));

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> resource.regenerateEmbeddings("valid-key", "not-valid", false, 1));
        assertEquals(400, ex.status());
        assertEquals("Validation Error", ex.title());
        verify(maintenanceService).startEmbeddingRegeneration("not-valid", false, 1);
    }

    @Test
    void regenerateEmbeddings_invalidBatchSize_returns400() {
        when(config.maintenance()).thenReturn(maintenanceConfig);
        when(maintenanceConfig.apiKey()).thenReturn(Optional.of("valid-key"));
        when(maintenanceConfig.allowedCidrs()).thenReturn(Optional.empty());
        when(maintenanceService.startEmbeddingRegeneration(null, true, 64))
                .thenThrow(new ValidationBusinessException("batchSize must be between 1 and 32"));

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> resource.regenerateEmbeddings("valid-key", null, true, 64));
        assertEquals(400, ex.status());
        assertEquals("Validation Error", ex.title());
    }

    @Test
    void regenerateEmbeddingsStatus_knownJob_returns200() {
        when(config.maintenance()).thenReturn(maintenanceConfig);
        when(maintenanceConfig.apiKey()).thenReturn(Optional.of("valid-key"));
        when(maintenanceConfig.allowedCidrs()).thenReturn(Optional.empty());
        UUID jobId = UUID.randomUUID();
        NodeEmbeddingMaintenanceService.EmbeddingRegenerationJobStatus status =
                new NodeEmbeddingMaintenanceService.EmbeddingRegenerationJobStatus(
                        "regenerate-embeddings",
                        jobId,
                        NodeEmbeddingMaintenanceService.JobState.RUNNING,
                        "ALL",
                        true,
                        16,
                        100,
                        32,
                        32,
                        0,
                        null,
                        Instant.now(),
                        Instant.now(),
                        null
                );
        when(maintenanceService.getEmbeddingRegenerationStatus(jobId.toString())).thenReturn(Optional.of(status));

        Response response = resource.regenerateEmbeddingsStatus("valid-key", jobId.toString());

        assertEquals(200, response.getStatus());
        assertSame(status, response.getEntity());
    }

    @Test
    void regenerateEmbeddingsStatus_unknownJob_returns404() {
        when(config.maintenance()).thenReturn(maintenanceConfig);
        when(maintenanceConfig.apiKey()).thenReturn(Optional.of("valid-key"));
        when(maintenanceConfig.allowedCidrs()).thenReturn(Optional.empty());
        UUID jobId = UUID.randomUUID();
        when(maintenanceService.getEmbeddingRegenerationStatus(jobId.toString())).thenReturn(Optional.empty());

        assertThrows(NotFoundBusinessException.class, () -> resource.regenerateEmbeddingsStatus("valid-key", jobId.toString()));
    }

    @Test
    void regenerateEmbeddingsStatus_invalidJobId_returns400() {
        when(config.maintenance()).thenReturn(maintenanceConfig);
        when(maintenanceConfig.apiKey()).thenReturn(Optional.of("valid-key"));
        when(maintenanceConfig.allowedCidrs()).thenReturn(Optional.empty());
        when(maintenanceService.getEmbeddingRegenerationStatus("not-a-uuid"))
                .thenThrow(new ValidationBusinessException("Invalid jobId format"));

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> resource.regenerateEmbeddingsStatus("valid-key", "not-a-uuid"));
        assertEquals(400, ex.status());
        assertEquals("Validation Error", ex.title());
        verify(maintenanceService).getEmbeddingRegenerationStatus("not-a-uuid");
    }
}
