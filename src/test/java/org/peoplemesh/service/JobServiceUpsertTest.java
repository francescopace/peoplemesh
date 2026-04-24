package org.peoplemesh.service;

import org.junit.jupiter.api.Test;
import org.peoplemesh.domain.dto.JobPostingDto;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.model.MeshNode;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class JobServiceUpsertTest {

    @Test
    void upsertFromIngest_newOpenJob_persistsAndAuditsCreated() {
        TestableJobService service = new TestableJobService();
        service.auditService = mock(AuditService.class);

        JobService.IngestJobPayload payload = new JobService.IngestJobPayload(
                "  Senior Engineer  ",
                "  Build APIs  ",
                "Java + Quarkus",
                List.of("Java", "Quarkus"),
                "REMOTE",
                "EMPLOYED",
                "IT",
                "open",
                "https://jobs.example/123"
        );

        JobPostingDto out = service.upsertFromIngest("workday", "ext-123", payload);

        assertTrue(service.persistCalled);
        assertFalse(service.deleteCalled);
        assertNotNull(out.id());
        assertEquals("Senior Engineer", out.title());
        assertEquals("Build APIs", out.description());
        assertEquals("IT", out.country());
        assertEquals(List.of("Java", "Quarkus"), out.skillsRequired());
        assertEquals("https://jobs.example/123", out.externalUrl());
        verify(service.auditService).log(any(UUID.class), eq("JOB_INGEST_CREATED"), eq("jobs_ingest"));
    }

    @Test
    void upsertFromIngest_existingClosedJob_deletesAndAuditsClosed() {
        MeshNode existing = new MeshNode();
        existing.id = UUID.randomUUID();
        existing.nodeType = NodeType.JOB;
        existing.createdAt = Instant.now();
        existing.updatedAt = Instant.now();
        existing.structuredData = Map.of("source", "workday", "external_id", "ext-1");

        TestableJobService service = new TestableJobService();
        service.auditService = mock(AuditService.class);
        service.existing = Optional.of(existing);

        JobService.IngestJobPayload payload = new JobService.IngestJobPayload(
                "Role", "Desc", "Req", List.of("Java"),
                null, null, "DE", "closed", null
        );

        JobPostingDto out = service.upsertFromIngest("workday", "ext-1", payload);

        assertFalse(service.persistCalled);
        assertTrue(service.deleteCalled);
        assertEquals(existing.id, out.id());
        verify(service.auditService).log(any(UUID.class), eq("JOB_INGEST_CLOSED"), eq("jobs_ingest"));
        verify(service.auditService, never()).log(any(UUID.class), eq("JOB_INGEST_UPDATED"), eq("jobs_ingest"));
    }

    @Test
    void upsertFromIngest_newClosedJob_returnsWithoutPersistOrAudit() {
        TestableJobService service = new TestableJobService();
        service.auditService = mock(AuditService.class);

        JobService.IngestJobPayload payload = new JobService.IngestJobPayload(
                "Role", "Desc", null, null,
                null, null, "FR", "archived", null
        );

        JobPostingDto out = service.upsertFromIngest("workday", "ext-9", payload);

        assertFalse(service.persistCalled);
        assertFalse(service.deleteCalled);
        assertEquals("Role", out.title());
        verify(service.auditService, never()).log(any(UUID.class), eq("JOB_INGEST_CREATED"), eq("jobs_ingest"));
        verify(service.auditService, never()).log(any(UUID.class), eq("JOB_INGEST_CLOSED"), eq("jobs_ingest"));
    }

    @Test
    void upsertFromIngest_existingOpenJob_auditsUpdated() {
        MeshNode existing = new MeshNode();
        existing.id = UUID.randomUUID();
        existing.nodeType = NodeType.JOB;
        existing.createdAt = Instant.now();
        existing.updatedAt = Instant.now();
        existing.structuredData = Map.of("source", "workday", "external_id", "ext-upd");

        TestableJobService service = new TestableJobService();
        service.auditService = mock(AuditService.class);
        service.existing = Optional.of(existing);

        JobService.IngestJobPayload payload = new JobService.IngestJobPayload(
                "Role", "Desc", null, List.of("SQL"),
                "HYBRID", null, "NL", "open", null
        );

        JobPostingDto out = service.upsertFromIngest("workday", "ext-upd", payload);

        assertTrue(service.persistCalled);
        assertFalse(service.deleteCalled);
        assertEquals(existing.id, out.id());
        verify(service.auditService).log(any(UUID.class), eq("JOB_INGEST_UPDATED"), eq("jobs_ingest"));
    }

    private static final class TestableJobService extends JobService {
        Optional<MeshNode> existing = Optional.empty();
        boolean persistCalled;
        boolean deleteCalled;

        @Override
        Optional<MeshNode> loadBySourceAndExternalId(String source, String externalId) {
            return existing;
        }

        @Override
        float[] generateEmbedding(String text) {
            return new float[]{0.1f, 0.2f};
        }

        @Override
        void persistNode(MeshNode node) {
            persistCalled = true;
            if (node.id == null) {
                node.id = UUID.randomUUID();
            }
            if (node.createdAt == null) {
                node.createdAt = Instant.now();
            }
            node.updatedAt = Instant.now();
        }

        @Override
        void deleteNode(MeshNode node) {
            deleteCalled = true;
            node.updatedAt = Instant.now();
        }
    }
}
