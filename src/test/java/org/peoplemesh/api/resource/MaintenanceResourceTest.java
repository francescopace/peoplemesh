package org.peoplemesh.api.resource;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.config.AppConfig;
import org.peoplemesh.domain.dto.MaintenanceTuningMatchesRequest;
import org.peoplemesh.domain.dto.MeshMatchResult;
import org.peoplemesh.domain.dto.MaintenanceNodeCandidateDto;
import org.peoplemesh.domain.dto.NodeListItemDto;
import org.peoplemesh.domain.dto.SearchOptions;
import org.peoplemesh.domain.dto.SearchQuery;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.exception.NotFoundBusinessException;
import org.peoplemesh.domain.exception.ValidationBusinessException;
import org.peoplemesh.service.MaintenanceService;
import org.peoplemesh.service.MatchesService;
import org.peoplemesh.service.NodeEmbeddingMaintenanceService;
import org.peoplemesh.service.NodeService;
import org.peoplemesh.util.IpAllowlistUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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
    NodeService nodeService;
    @Mock
    MatchesService matchesService;
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
    void purgeConsentTokens_validKey_returnsResult() {
        when(config.maintenance()).thenReturn(maintenanceConfig);
        when(maintenanceConfig.apiKey()).thenReturn(Optional.of("valid-key"));
        when(maintenanceConfig.allowedCidrs()).thenReturn(Optional.empty());
        Map<String, Object> result = Map.of("action", "purge-consent-tokens", "purged", 3);
        when(maintenanceService.purgeConsentTokens()).thenReturn(result);

        Response response = resource.purgeConsentTokens("valid-key");

        assertEquals(200, response.getStatus());
        assertSame(result, response.getEntity());
        verify(maintenanceService).purgeConsentTokens();
    }

    @Test
    void enforceRetention_validKey_returnsResult() {
        when(config.maintenance()).thenReturn(maintenanceConfig);
        when(maintenanceConfig.apiKey()).thenReturn(Optional.of("valid-key"));
        when(maintenanceConfig.allowedCidrs()).thenReturn(Optional.empty());
        Map<String, Object> result = Map.of("action", "enforce-retention", "deleted", 5);
        when(maintenanceService.enforceRetention()).thenReturn(result);

        Response response = resource.enforceRetention("valid-key");

        assertEquals(200, response.getStatus());
        assertSame(result, response.getEntity());
        verify(maintenanceService).enforceRetention();
    }

    @Test
    void runClustering_validKey_returnsResult() {
        when(config.maintenance()).thenReturn(maintenanceConfig);
        when(maintenanceConfig.apiKey()).thenReturn(Optional.of("valid-key"));
        when(maintenanceConfig.allowedCidrs()).thenReturn(Optional.empty());
        Map<String, Object> result = Map.of("action", "run-clustering", "status", "completed");
        when(maintenanceService.runClustering()).thenReturn(result);

        Response response = resource.runClustering("valid-key");

        assertEquals(200, response.getStatus());
        assertSame(result, response.getEntity());
        verify(maintenanceService).runClustering();
    }

    @Test
    void listNodes_validKey_returnsCandidates() {
        when(config.maintenance()).thenReturn(maintenanceConfig);
        when(maintenanceConfig.apiKey()).thenReturn(Optional.of("valid-key"));
        when(maintenanceConfig.allowedCidrs()).thenReturn(Optional.empty());
        NodeListItemDto node = new NodeListItemDto(
                UUID.randomUUID(),
                NodeType.USER,
                "Alice",
                "Engineer",
                List.of("Java"),
                Map.of("skills_soft", List.of("communication")),
                "IT",
                true,
                Instant.now(),
                Instant.now(),
                null
        );
        when(nodeService.listNodes(NodeType.USER, true, 0, 20, true, false)).thenReturn(List.of(node));

        Response response = resource.listNodes("valid-key", "user", true, 0, 20);

        assertEquals(200, response.getStatus());
        @SuppressWarnings("unchecked")
        List<MaintenanceNodeCandidateDto> body = (List<MaintenanceNodeCandidateDto>) response.getEntity();
        assertEquals(1, body.size());
        assertEquals("Alice", body.getFirst().title());
        verify(nodeService).listNodes(NodeType.USER, true, 0, 20, true, false);
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

        ValidationBusinessException ex = assertThrows(
                ValidationBusinessException.class,
                () -> resource.regenerateEmbeddings("valid-key", "not-valid", false, 1));
        assertEquals("Invalid nodeType", ex.publicDetail());
        verify(maintenanceService).startEmbeddingRegeneration("not-valid", false, 1);
    }

    @Test
    void regenerateEmbeddings_invalidBatchSize_returns400() {
        when(config.maintenance()).thenReturn(maintenanceConfig);
        when(maintenanceConfig.apiKey()).thenReturn(Optional.of("valid-key"));
        when(maintenanceConfig.allowedCidrs()).thenReturn(Optional.empty());
        when(maintenanceService.startEmbeddingRegeneration(null, true, 64))
                .thenThrow(new ValidationBusinessException("batchSize must be between 1 and 32"));

        ValidationBusinessException ex = assertThrows(
                ValidationBusinessException.class,
                () -> resource.regenerateEmbeddings("valid-key", null, true, 64));
        assertEquals("batchSize must be between 1 and 32", ex.publicDetail());
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

        ValidationBusinessException ex = assertThrows(
                ValidationBusinessException.class,
                () -> resource.regenerateEmbeddingsStatus("valid-key", "not-a-uuid"));
        assertEquals("Invalid jobId format", ex.publicDetail());
        verify(maintenanceService).getEmbeddingRegenerationStatus("not-a-uuid");
    }

    @Test
    void tuneMatchMyProfile_validKey_returnsMatches() {
        when(config.maintenance()).thenReturn(maintenanceConfig);
        when(maintenanceConfig.apiKey()).thenReturn(Optional.of("valid-key"));
        when(maintenanceConfig.allowedCidrs()).thenReturn(Optional.empty());
        UUID userId = UUID.randomUUID();
        SearchOptions options = new SearchOptions(
                0.8, null, null, null, null, null, null,
                null, null, null, null, null);
        SearchQuery query = new SearchQuery(null, null, "unknown", null, List.of(), "search", "all");
        MaintenanceTuningMatchesRequest request = new MaintenanceTuningMatchesRequest(
                query, options, "PEOPLE", "IT", 10, 0);
        List<MeshMatchResult> expected = List.of(new MeshMatchResult(
                UUID.randomUUID(), "PEOPLE", "Test", null, null, List.of(), "IT", 0.9, null, null));
        when(matchesService.matchMyProfile(userId, "PEOPLE", "IT", 10, 0, options))
                .thenReturn(expected);

        Response response = resource.tuneMatchMyProfile("valid-key", userId.toString(), request);

        assertEquals(200, response.getStatus());
        assertSame(expected, response.getEntity());
    }

    @Test
    void tuneMatchMyProfile_withoutSearchOptions_returnsMatches() {
        when(config.maintenance()).thenReturn(maintenanceConfig);
        when(maintenanceConfig.apiKey()).thenReturn(Optional.of("valid-key"));
        when(maintenanceConfig.allowedCidrs()).thenReturn(Optional.empty());
        UUID userId = UUID.randomUUID();
        SearchQuery query = new SearchQuery(null, null, "unknown", null, List.of(), "search", "all");
        MaintenanceTuningMatchesRequest request = new MaintenanceTuningMatchesRequest(
                query, null, "PEOPLE", "IT", 10, 0);
        List<MeshMatchResult> expected = List.of();
        when(matchesService.matchMyProfile(userId, "PEOPLE", "IT", 10, 0, null))
                .thenReturn(expected);

        Response response = resource.tuneMatchMyProfile("valid-key", userId.toString(), request);

        assertEquals(200, response.getStatus());
        assertSame(expected, response.getEntity());
    }
}
