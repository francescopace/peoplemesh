package org.peoplemesh.api.resource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.domain.dto.AtsIngestRequestDto;
import org.peoplemesh.domain.dto.AtsIngestResultDto;
import org.peoplemesh.service.AtsIngestService;

import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AtsIngestResourceTest {

    @Mock AppConfig config;
    @Mock AtsIngestService atsIngestService;
    @Mock HttpHeaders httpHeaders;

    @InjectMocks
    AtsIngestResource resource;

    @BeforeEach
    void setUp() throws Exception {
        Field headersField = AtsIngestResource.class.getDeclaredField("httpHeaders");
        headersField.setAccessible(true);
        headersField.set(resource, httpHeaders);

        AppConfig.MaintenanceConfig maintenanceConfig = mock(AppConfig.MaintenanceConfig.class);
        lenient().when(config.maintenance()).thenReturn(maintenanceConfig);
        lenient().when(maintenanceConfig.apiKey()).thenReturn(Optional.of("test-key"));
        lenient().when(maintenanceConfig.allowedCidrs()).thenReturn(Optional.empty());
    }

    @Test
    void ingestJobs_nullRequest_returns400() {
        Response response = resource.ingestJobs("test-key", null);
        assertEquals(400, response.getStatus());
    }

    @Test
    void ingestJobs_emptyJobs_returns400() {
        AtsIngestRequestDto req = new AtsIngestRequestDto();
        req.jobs = Collections.emptyList();

        when(atsIngestService.ingestJobs(req)).thenThrow(new IllegalArgumentException("jobs array is required"));
        assertThrows(IllegalArgumentException.class, () -> resource.ingestJobs("test-key", req));
    }

    @Test
    void ingestJobs_noOwner_returns400() {
        AtsIngestRequestDto req = new AtsIngestRequestDto();
        var entry = new org.peoplemesh.domain.dto.AtsJobEntryDto();
        entry.externalId = "ext-1";
        entry.title = "Dev";
        entry.description = "Build";
        req.jobs = List.of(entry);
        req.ownerUserId = null;

        when(atsIngestService.ingestJobs(req)).thenThrow(new IllegalArgumentException("owner_user_id is required"));
        assertThrows(IllegalArgumentException.class, () -> resource.ingestJobs("test-key", req));
    }

    @Test
    void ingestJobs_exceedsBatchSize_returns400() {
        AtsIngestRequestDto req = new AtsIngestRequestDto();
        req.ownerUserId = UUID.randomUUID();
        List<org.peoplemesh.domain.dto.AtsJobEntryDto> jobs = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            var entry = new org.peoplemesh.domain.dto.AtsJobEntryDto();
            entry.externalId = "ext-" + i;
            jobs.add(entry);
        }
        req.jobs = jobs;

        when(atsIngestService.ingestJobs(req)).thenThrow(new IllegalArgumentException("batch size exceeds maximum"));
        assertThrows(IllegalArgumentException.class, () -> resource.ingestJobs("test-key", req));
    }

    @Test
    void ingestJobs_validBatch_returnsResult() {
        UUID owner = UUID.randomUUID();
        AtsIngestRequestDto req = new AtsIngestRequestDto();
        req.ownerUserId = owner;

        var entry = new org.peoplemesh.domain.dto.AtsJobEntryDto();
        entry.externalId = "ext-1";
        entry.title = "Backend Dev";
        entry.description = "Java development";
        req.jobs = List.of(entry);

        when(atsIngestService.ingestJobs(req))
                .thenReturn(new AtsIngestResultDto(1, 0, List.of()));

        Response response = resource.ingestJobs("test-key", req);
        assertEquals(200, response.getStatus());

        AtsIngestResultDto body = (AtsIngestResultDto) response.getEntity();
        assertEquals(1, body.upserted());
        assertEquals(0, body.failed());
    }

    @Test
    void ingestJobs_wrongKey_throwsForbidden() {
        AtsIngestRequestDto req = new AtsIngestRequestDto();
        req.ownerUserId = UUID.randomUUID();
        var entry = new org.peoplemesh.domain.dto.AtsJobEntryDto();
        entry.externalId = "ext-1";
        entry.title = "Dev";
        entry.description = "Dev";
        req.jobs = List.of(entry);

        assertThrows(ForbiddenException.class,
                () -> resource.ingestJobs("wrong-key", req));
        verifyNoInteractions(atsIngestService);
    }

    @Test
    void ingestJobs_entryMissingExternalId_countsAsError() {
        UUID owner = UUID.randomUUID();
        AtsIngestRequestDto req = new AtsIngestRequestDto();
        req.ownerUserId = owner;

        var entry = new org.peoplemesh.domain.dto.AtsJobEntryDto();
        entry.externalId = null;
        entry.title = "Dev";
        entry.description = "Desc";
        req.jobs = List.of(entry);

        when(atsIngestService.ingestJobs(req))
                .thenReturn(new AtsIngestResultDto(0, 1, List.of(Map.of("external_id", "", "error", "Failed"))));
        Response response = resource.ingestJobs("test-key", req);
        assertEquals(200, response.getStatus());

        AtsIngestResultDto body = (AtsIngestResultDto) response.getEntity();
        assertEquals(0, body.upserted());
        assertEquals(1, body.failed());
    }

    @Test
    void ingestJobs_entryMissingTitle_countsAsError() {
        UUID owner = UUID.randomUUID();
        AtsIngestRequestDto req = new AtsIngestRequestDto();
        req.ownerUserId = owner;

        var entry = new org.peoplemesh.domain.dto.AtsJobEntryDto();
        entry.externalId = "ext-1";
        entry.title = null;
        entry.description = "Desc";
        req.jobs = List.of(entry);

        when(atsIngestService.ingestJobs(req))
                .thenReturn(new AtsIngestResultDto(0, 1, List.of(Map.of("external_id", "ext-1", "error", "Failed"))));
        Response response = resource.ingestJobs("test-key", req);

        AtsIngestResultDto body = (AtsIngestResultDto) response.getEntity();
        assertEquals(1, body.failed());
    }
}
