package org.peoplemesh.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.domain.dto.JobPostingDto;
import org.peoplemesh.service.JobService;

import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AtsIngestResourceTest {

    @Mock AppConfig config;
    @Mock JobService jobService;
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
        AtsIngestResource.AtsIngestRequest req = new AtsIngestResource.AtsIngestRequest();
        req.jobs = Collections.emptyList();

        Response response = resource.ingestJobs("test-key", req);
        assertEquals(400, response.getStatus());
    }

    @Test
    void ingestJobs_noOwner_returns400() {
        AtsIngestResource.AtsIngestRequest req = new AtsIngestResource.AtsIngestRequest();
        AtsIngestResource.AtsJobEntry entry = new AtsIngestResource.AtsJobEntry();
        entry.externalId = "ext-1";
        entry.title = "Dev";
        entry.description = "Build";
        req.jobs = List.of(entry);
        req.ownerUserId = null;

        Response response = resource.ingestJobs("test-key", req);
        assertEquals(400, response.getStatus());
    }

    @Test
    void ingestJobs_exceedsBatchSize_returns400() {
        AtsIngestResource.AtsIngestRequest req = new AtsIngestResource.AtsIngestRequest();
        req.ownerUserId = UUID.randomUUID();
        List<AtsIngestResource.AtsJobEntry> jobs = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            AtsIngestResource.AtsJobEntry entry = new AtsIngestResource.AtsJobEntry();
            entry.externalId = "ext-" + i;
            jobs.add(entry);
        }
        req.jobs = jobs;

        Response response = resource.ingestJobs("test-key", req);
        assertEquals(400, response.getStatus());
    }

    @Test
    void ingestJobs_validBatch_returnsResult() {
        UUID owner = UUID.randomUUID();
        AtsIngestResource.AtsIngestRequest req = new AtsIngestResource.AtsIngestRequest();
        req.ownerUserId = owner;

        AtsIngestResource.AtsJobEntry entry = new AtsIngestResource.AtsJobEntry();
        entry.externalId = "ext-1";
        entry.title = "Backend Dev";
        entry.description = "Java development";
        req.jobs = List.of(entry);

        JobPostingDto dto = mock(JobPostingDto.class);
        when(jobService.upsertFromAts(eq(owner), eq("ext-1"), any())).thenReturn(dto);

        Response response = resource.ingestJobs("test-key", req);
        assertEquals(200, response.getStatus());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertEquals(1, body.get("upserted"));
        assertEquals(0, body.get("failed"));
    }

    @Test
    void ingestJobs_wrongKey_throwsForbidden() {
        AtsIngestResource.AtsIngestRequest req = new AtsIngestResource.AtsIngestRequest();
        req.ownerUserId = UUID.randomUUID();
        AtsIngestResource.AtsJobEntry entry = new AtsIngestResource.AtsJobEntry();
        entry.externalId = "ext-1";
        entry.title = "Dev";
        entry.description = "Dev";
        req.jobs = List.of(entry);

        assertThrows(ForbiddenException.class,
                () -> resource.ingestJobs("wrong-key", req));
    }

    @Test
    void ingestJobs_entryMissingExternalId_countsAsError() {
        UUID owner = UUID.randomUUID();
        AtsIngestResource.AtsIngestRequest req = new AtsIngestResource.AtsIngestRequest();
        req.ownerUserId = owner;

        AtsIngestResource.AtsJobEntry entry = new AtsIngestResource.AtsJobEntry();
        entry.externalId = null;
        entry.title = "Dev";
        entry.description = "Desc";
        req.jobs = List.of(entry);

        Response response = resource.ingestJobs("test-key", req);
        assertEquals(200, response.getStatus());

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertEquals(0, body.get("upserted"));
        assertEquals(1, body.get("failed"));
    }

    @Test
    void ingestJobs_entryMissingTitle_countsAsError() {
        UUID owner = UUID.randomUUID();
        AtsIngestResource.AtsIngestRequest req = new AtsIngestResource.AtsIngestRequest();
        req.ownerUserId = owner;

        AtsIngestResource.AtsJobEntry entry = new AtsIngestResource.AtsJobEntry();
        entry.externalId = "ext-1";
        entry.title = null;
        entry.description = "Desc";
        req.jobs = List.of(entry);

        Response response = resource.ingestJobs("test-key", req);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertEquals(1, body.get("failed"));
    }
}
