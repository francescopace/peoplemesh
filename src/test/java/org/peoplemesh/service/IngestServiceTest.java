package org.peoplemesh.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.domain.dto.IngestResultDto;
import org.peoplemesh.domain.dto.NodeIngestEntryDto;
import org.peoplemesh.domain.dto.NodesIngestRequestDto;
import org.peoplemesh.domain.dto.JobPostingDto;
import org.peoplemesh.domain.dto.UserIngestEntryDto;
import org.peoplemesh.domain.dto.UsersIngestRequestDto;
import org.peoplemesh.domain.enums.EmploymentType;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.enums.WorkMode;
import org.peoplemesh.domain.exception.ValidationBusinessException;
import org.peoplemesh.domain.model.MeshNode;
import org.peoplemesh.repository.NodeRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngestServiceTest {

    @Mock
    JobService jobService;

    @Mock
    NodeRepository nodeRepository;

    @Mock
    EmbeddingService embeddingService;

    @InjectMocks
    IngestService service;

    @Test
    void ingestNodes_nullRequest_throwsValidationError() {
        assertThrows(ValidationBusinessException.class, () -> service.ingestNodes(null));
    }

    @Test
    void ingestNodes_emptyNodes_throwsValidationError() {
        NodesIngestRequestDto request = new NodesIngestRequestDto();
        request.nodes = List.of();
        assertThrows(ValidationBusinessException.class, () -> service.ingestNodes(request));
    }

    @Test
    void ingestNodes_validRequest_returnsUpsertCounters() {
        NodesIngestRequestDto request = new NodesIngestRequestDto();
        request.nodes = List.of(job("ext-1"));

        when(jobService.upsertFromIngest(eq("workday"), eq("ext-1"), any())).thenReturn(mockPosting());

        IngestResultDto result = service.ingestNodes(request);
        assertEquals(1, result.upserted());
        assertEquals(0, result.failed());
    }

    @Test
    void ingestNodes_whenUpsertThrows_recordsFailedEntry() {
        NodesIngestRequestDto request = new NodesIngestRequestDto();
        request.nodes = List.of(job("ext-fail"));
        when(jobService.upsertFromIngest(eq("workday"), eq("ext-fail"), any())).thenThrow(new RuntimeException("boom"));

        IngestResultDto result = service.ingestNodes(request);
        assertEquals(0, result.upserted());
        assertEquals(1, result.failed());
    }

    @Test
    void ingestNodes_nonJobNode_upsertsGenericNode() {
        NodesIngestRequestDto request = new NodesIngestRequestDto();
        NodeIngestEntryDto entry = new NodeIngestEntryDto();
        entry.nodeType = "PROJECT";
        entry.source = "internal";
        entry.externalId = "prj-1";
        entry.title = "Project One";
        entry.description = "Description";
        entry.country = "IT";
        entry.tags = List.of("java");
        entry.searchable = null;
        entry.structuredData = Map.of("team", "platform");
        request.nodes = List.of(entry);

        when(nodeRepository.findByTypeAndSourceAndExternalId(NodeType.PROJECT, "internal", "prj-1"))
                .thenReturn(Optional.empty());
        when(embeddingService.generateEmbedding(any())).thenReturn(new float[]{0.1f, 0.2f});

        IngestResultDto result = service.ingestNodes(request);

        assertEquals(1, result.upserted());
        assertEquals(0, result.failed());
        verify(nodeRepository).persist(argThat(node ->
                node.nodeType == NodeType.PROJECT &&
                        "prj-1".equals(node.externalId) &&
                        Boolean.TRUE.equals(node.searchable) &&
                        "internal".equals(node.structuredData.get("source")) &&
                        "prj-1".equals(node.structuredData.get("external_id")) &&
                        "platform".equals(node.structuredData.get("team")) &&
                        node.embedding != null &&
                        node.embedding.length == 2));
    }

    @Test
    void ingestNodes_batchTooLarge_throwsValidationError() {
        NodesIngestRequestDto request = new NodesIngestRequestDto();
        List<NodeIngestEntryDto> entries = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            entries.add(job("ext-" + i));
        }
        request.nodes = entries;

        assertThrows(ValidationBusinessException.class, () -> service.ingestNodes(request));
    }

    @Test
    void ingestUsers_nullRequest_throwsValidationError() {
        assertThrows(ValidationBusinessException.class, () -> service.ingestUsers(null));
    }

    @Test
    void ingestUsers_emptyUsers_throwsValidationError() {
        UsersIngestRequestDto request = new UsersIngestRequestDto();
        request.users = List.of();

        assertThrows(ValidationBusinessException.class, () -> service.ingestUsers(request));
    }

    @Test
    void ingestUsers_batchTooLarge_throwsValidationError() {
        UsersIngestRequestDto request = new UsersIngestRequestDto();
        List<UserIngestEntryDto> users = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            users.add(user("user-" + i, null));
        }
        request.users = users;

        assertThrows(ValidationBusinessException.class, () -> service.ingestUsers(request));
    }

    @Test
    void ingestUsers_newNodeWithNodeId_setsIdentityAndPersists() {
        UsersIngestRequestDto request = new UsersIngestRequestDto();
        UUID nodeId = UUID.randomUUID();
        UserIngestEntryDto entry = user("u-1", nodeId);
        entry.searchable = null;
        entry.structuredData = Map.of("source", "hr");
        request.users = List.of(entry);

        when(nodeRepository.findUserByExternalId("u-1")).thenReturn(Optional.empty());

        IngestResultDto result = service.ingestUsers(request);

        assertEquals(1, result.upserted());
        assertEquals(0, result.failed());
        verify(nodeRepository).persist(argThat(node ->
                node.id.equals(nodeId) &&
                        node.nodeType == NodeType.USER &&
                        Boolean.TRUE.equals(node.searchable) &&
                        node.structuredData != null &&
                        "hr".equals(node.structuredData.get("source"))));
    }

    @Test
    void ingestUsers_existingNode_keepsExistingIdAndAllowsNullStructuredData() {
        UsersIngestRequestDto request = new UsersIngestRequestDto();
        UUID existingId = UUID.randomUUID();
        UUID ignoredNodeId = UUID.randomUUID();
        UserIngestEntryDto entry = user("u-2", ignoredNodeId);
        entry.searchable = false;
        entry.structuredData = null;
        request.users = List.of(entry);

        MeshNode existing = new MeshNode();
        existing.id = existingId;
        when(nodeRepository.findUserByExternalId("u-2")).thenReturn(Optional.of(existing));

        IngestResultDto result = service.ingestUsers(request);

        assertEquals(1, result.upserted());
        assertEquals(0, result.failed());
        verify(nodeRepository).persist(argThat(node ->
                node.id.equals(existingId) &&
                        !node.searchable &&
                        node.structuredData == null));
    }

    @Test
    void ingestUsers_whenPersistThrows_recordsFailureWithEmptyExternalIdFallback() {
        UsersIngestRequestDto request = new UsersIngestRequestDto();
        UserIngestEntryDto entry = user(null, null);
        request.users = List.of(entry);

        when(nodeRepository.findUserByExternalId(null)).thenReturn(Optional.empty());
        doThrow(new RuntimeException("db down")).when(nodeRepository).persist(any(MeshNode.class));

        IngestResultDto result = service.ingestUsers(request);

        assertEquals(0, result.upserted());
        assertEquals(1, result.failed());
        assertNotNull(result.errors());
        assertEquals("", result.errors().getFirst().get("external_id"));
    }

    private static NodeIngestEntryDto job(String externalId) {
        NodeIngestEntryDto entry = new NodeIngestEntryDto();
        entry.nodeType = "JOB";
        entry.source = "workday";
        entry.externalId = externalId;
        entry.title = "Engineer";
        entry.description = "Desc";
        entry.requirementsText = "Req";
        entry.skillsRequired = List.of("java");
        entry.workMode = "REMOTE";
        entry.employmentType = "EMPLOYED";
        entry.country = "IT";
        entry.status = "OPEN";
        return entry;
    }

    private static JobPostingDto mockPosting() {
        return new JobPostingDto(
                UUID.randomUUID(),
                "Engineer",
                "Desc",
                "Req",
                List.of("java"),
                WorkMode.REMOTE,
                EmploymentType.EMPLOYED,
                "IT",
                null,
                null,
                null,
                null
        );
    }

    private static UserIngestEntryDto user(String externalId, UUID nodeId) {
        UserIngestEntryDto entry = new UserIngestEntryDto();
        entry.externalId = externalId;
        entry.nodeId = nodeId;
        entry.title = "User";
        entry.description = "Desc";
        entry.country = "IT";
        entry.tags = List.of("backend");
        return entry;
    }
}
