package org.peoplemesh.api.resource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.domain.dto.IngestResultDto;
import org.peoplemesh.domain.dto.NodesIngestRequestDto;
import org.peoplemesh.domain.dto.UsersIngestRequestDto;
import org.peoplemesh.service.IngestService;

import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IngestResourceTest {

    @Mock AppConfig config;
    @Mock IngestService ingestService;
    @Mock HttpHeaders httpHeaders;

    @InjectMocks
    IngestResource resource;

    @BeforeEach
    void setUp() throws Exception {
        Field headersField = IngestResource.class.getDeclaredField("httpHeaders");
        headersField.setAccessible(true);
        headersField.set(resource, httpHeaders);

        AppConfig.MaintenanceConfig maintenanceConfig = mock(AppConfig.MaintenanceConfig.class);
        lenient().when(config.maintenance()).thenReturn(maintenanceConfig);
        lenient().when(maintenanceConfig.apiKey()).thenReturn(Optional.of("test-key"));
        lenient().when(maintenanceConfig.allowedCidrs()).thenReturn(Optional.empty());
    }

    @Test
    void ingestNodes_nullRequest_returns400() {
        Response response = resource.ingestNodes("test-key", null);
        assertEquals(400, response.getStatus());
    }

    @Test
    void ingestNodes_emptyNodes_returns400() {
        NodesIngestRequestDto req = new NodesIngestRequestDto();
        req.nodes = Collections.emptyList();

        when(ingestService.ingestNodes(req)).thenThrow(new IllegalArgumentException("nodes array is required"));
        assertThrows(IllegalArgumentException.class, () -> resource.ingestNodes("test-key", req));
    }

    @Test
    void ingestNodes_missingSource_isServiceValidationError() {
        NodesIngestRequestDto req = new NodesIngestRequestDto();
        var entry = new org.peoplemesh.domain.dto.NodeIngestEntryDto();
        entry.nodeType = "JOB";
        entry.externalId = "ext-1";
        entry.title = "Dev";
        entry.description = "Build";
        entry.source = null;
        req.nodes = List.of(entry);

        when(ingestService.ingestNodes(req)).thenThrow(new IllegalArgumentException("source is required"));
        assertThrows(IllegalArgumentException.class, () -> resource.ingestNodes("test-key", req));
    }

    @Test
    void ingestNodes_exceedsBatchSize_returns400() {
        NodesIngestRequestDto req = new NodesIngestRequestDto();
        List<org.peoplemesh.domain.dto.NodeIngestEntryDto> nodes = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            var entry = new org.peoplemesh.domain.dto.NodeIngestEntryDto();
            entry.nodeType = "JOB";
            entry.source = "workday";
            entry.externalId = "ext-" + i;
            nodes.add(entry);
        }
        req.nodes = nodes;

        when(ingestService.ingestNodes(req)).thenThrow(new IllegalArgumentException("batch size exceeds maximum"));
        assertThrows(IllegalArgumentException.class, () -> resource.ingestNodes("test-key", req));
    }

    @Test
    void ingestNodes_validBatch_returnsResult() {
        NodesIngestRequestDto req = new NodesIngestRequestDto();

        var entry = new org.peoplemesh.domain.dto.NodeIngestEntryDto();
        entry.nodeType = "JOB";
        entry.source = "workday";
        entry.externalId = "ext-1";
        entry.title = "Backend Dev";
        entry.description = "Java development";
        req.nodes = List.of(entry);

        when(ingestService.ingestNodes(req))
                .thenReturn(new IngestResultDto(1, 0, List.of()));

        Response response = resource.ingestNodes("test-key", req);
        assertEquals(200, response.getStatus());

        IngestResultDto body = (IngestResultDto) response.getEntity();
        assertEquals(1, body.upserted());
        assertEquals(0, body.failed());
    }

    @Test
    void ingestNodes_wrongKey_throwsForbidden() {
        NodesIngestRequestDto req = new NodesIngestRequestDto();
        var entry = new org.peoplemesh.domain.dto.NodeIngestEntryDto();
        entry.nodeType = "JOB";
        entry.source = "workday";
        entry.externalId = "ext-1";
        entry.title = "Dev";
        entry.description = "Dev";
        req.nodes = List.of(entry);

        assertThrows(ForbiddenException.class,
                () -> resource.ingestNodes("wrong-key", req));
        verifyNoInteractions(ingestService);
    }

    @Test
    void ingestNodes_entryMissingExternalId_countsAsError() {
        NodesIngestRequestDto req = new NodesIngestRequestDto();

        var entry = new org.peoplemesh.domain.dto.NodeIngestEntryDto();
        entry.nodeType = "JOB";
        entry.source = "workday";
        entry.externalId = null;
        entry.title = "Dev";
        entry.description = "Desc";
        req.nodes = List.of(entry);

        when(ingestService.ingestNodes(req))
                .thenReturn(new IngestResultDto(0, 1, List.of(Map.of("external_id", "", "error", "Failed"))));
        Response response = resource.ingestNodes("test-key", req);
        assertEquals(200, response.getStatus());

        IngestResultDto body = (IngestResultDto) response.getEntity();
        assertEquals(0, body.upserted());
        assertEquals(1, body.failed());
    }

    @Test
    void ingestNodes_entryMissingTitle_countsAsError() {
        NodesIngestRequestDto req = new NodesIngestRequestDto();

        var entry = new org.peoplemesh.domain.dto.NodeIngestEntryDto();
        entry.nodeType = "JOB";
        entry.source = "workday";
        entry.externalId = "ext-1";
        entry.title = null;
        entry.description = "Desc";
        req.nodes = List.of(entry);

        when(ingestService.ingestNodes(req))
                .thenReturn(new IngestResultDto(0, 1, List.of(Map.of("external_id", "ext-1", "error", "Failed"))));
        Response response = resource.ingestNodes("test-key", req);

        IngestResultDto body = (IngestResultDto) response.getEntity();
        assertEquals(1, body.failed());
    }

    @Test
    void ingestUsers_nullRequest_returns400() {
        Response response = resource.ingestUsers("test-key", null);
        assertEquals(400, response.getStatus());
    }

    @Test
    void ingestUsers_validBatch_returnsResult() {
        UsersIngestRequestDto req = new UsersIngestRequestDto();
        var entry = new org.peoplemesh.domain.dto.UserIngestEntryDto();
        entry.externalId = "u-1";
        entry.title = "User";
        entry.description = "Desc";
        req.users = List.of(entry);
        when(ingestService.ingestUsers(req)).thenReturn(new IngestResultDto(1, 0, List.of()));

        Response response = resource.ingestUsers("test-key", req);
        assertEquals(200, response.getStatus());
    }
}
