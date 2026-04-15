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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class JobServiceUpsertTest {

    @Test
    void upsertFromAts_newOpenJob_persistsAndAuditsCreated() {
        UUID owner = UUID.randomUUID();
        TestableJobService service = new TestableJobService();
        service.auditService = mock(AuditService.class);

        JobService.AtsJobPayload payload = new JobService.AtsJobPayload(
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

        JobPostingDto out = service.upsertFromAts(owner, "ext-123", payload);

        assertTrue(service.persistCalled);
        assertFalse(service.deleteCalled);
        assertNotNull(out.id());
        assertEquals("Senior Engineer", out.title());
        assertEquals("Build APIs", out.description());
        assertEquals("IT", out.country());
        assertEquals(List.of("Java", "Quarkus"), out.skillsRequired());
        assertEquals("https://jobs.example/123", out.externalUrl());
        verify(service.auditService).log(owner, "JOB_ATS_CREATED", "ats_ingest");
    }

    @Test
    void upsertFromAts_existingClosedJob_deletesAndAuditsClosed() {
        UUID owner = UUID.randomUUID();
        MeshNode existing = new MeshNode();
        existing.id = UUID.randomUUID();
        existing.nodeType = NodeType.JOB;
        existing.createdBy = owner;
        existing.createdAt = Instant.now();
        existing.updatedAt = Instant.now();
        existing.structuredData = Map.of("external_id", "ext-1");

        TestableJobService service = new TestableJobService();
        service.auditService = mock(AuditService.class);
        service.existing = Optional.of(existing);

        JobService.AtsJobPayload payload = new JobService.AtsJobPayload(
                "Role", "Desc", "Req", List.of("Java"),
                null, null, "DE", "closed", null
        );

        JobPostingDto out = service.upsertFromAts(owner, "ext-1", payload);

        assertFalse(service.persistCalled);
        assertTrue(service.deleteCalled);
        assertEquals(existing.id, out.id());
        verify(service.auditService).log(owner, "JOB_ATS_CLOSED", "ats_ingest");
        verify(service.auditService, never()).log(owner, "JOB_ATS_UPDATED", "ats_ingest");
    }

    @Test
    void upsertFromAts_newClosedJob_returnsWithoutPersistOrAudit() {
        UUID owner = UUID.randomUUID();
        TestableJobService service = new TestableJobService();
        service.auditService = mock(AuditService.class);

        JobService.AtsJobPayload payload = new JobService.AtsJobPayload(
                "Role", "Desc", null, null,
                null, null, "FR", "archived", null
        );

        JobPostingDto out = service.upsertFromAts(owner, "ext-9", payload);

        assertFalse(service.persistCalled);
        assertFalse(service.deleteCalled);
        assertEquals("Role", out.title());
        verify(service.auditService, never()).log(owner, "JOB_ATS_CREATED", "ats_ingest");
        verify(service.auditService, never()).log(owner, "JOB_ATS_CLOSED", "ats_ingest");
    }

    @Test
    void upsertFromAts_existingOpenJob_auditsUpdated() {
        UUID owner = UUID.randomUUID();
        MeshNode existing = new MeshNode();
        existing.id = UUID.randomUUID();
        existing.nodeType = NodeType.JOB;
        existing.createdBy = owner;
        existing.createdAt = Instant.now();
        existing.updatedAt = Instant.now();

        TestableJobService service = new TestableJobService();
        service.auditService = mock(AuditService.class);
        service.existing = Optional.of(existing);

        JobService.AtsJobPayload payload = new JobService.AtsJobPayload(
                "Role", "Desc", null, List.of("SQL"),
                "HYBRID", null, "NL", "open", null
        );

        JobPostingDto out = service.upsertFromAts(owner, "ext-upd", payload);

        assertTrue(service.persistCalled);
        assertFalse(service.deleteCalled);
        assertEquals(existing.id, out.id());
        verify(service.auditService).log(owner, "JOB_ATS_UPDATED", "ats_ingest");
    }

    private static final class TestableJobService extends JobService {
        Optional<MeshNode> existing = Optional.empty();
        boolean persistCalled;
        boolean deleteCalled;

        @Override
        Optional<MeshNode> loadByExternalId(UUID ownerUserId, String externalId) {
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
            node.closedAt = Instant.now();
            node.updatedAt = Instant.now();
        }
    }
}
