package org.peoplemesh.domain.dto;

import org.junit.jupiter.api.Test;
import org.peoplemesh.domain.enums.EmploymentType;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.enums.WorkMode;
import org.peoplemesh.domain.model.MeshNode;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JobPostingDtoTest {

    @Test
    void fromMeshNode_validJob_mapsAllFields() {
        MeshNode node = new MeshNode();
        node.id = UUID.randomUUID();
        node.nodeType = NodeType.JOB;
        node.title = "Backend Dev";
        node.description = "Build APIs";
        node.country = "IT";
        node.tags = List.of("Java", "Quarkus");
        node.structuredData = Map.of(
                "requirements_text", "3+ years",
                "work_mode", "REMOTE",
                "employment_type", "EMPLOYED",
                "external_url", "https://example.com/job/1"
        );
        node.createdAt = Instant.now();
        node.updatedAt = Instant.now();

        JobPostingDto dto = JobPostingDto.fromMeshNode(node);

        assertEquals(node.id, dto.id());
        assertEquals("Backend Dev", dto.title());
        assertEquals("Build APIs", dto.description());
        assertEquals("3+ years", dto.requirementsText());
        assertEquals(List.of("Java", "Quarkus"), dto.skillsRequired());
        assertEquals(WorkMode.REMOTE, dto.workMode());
        assertEquals(EmploymentType.EMPLOYED, dto.employmentType());
        assertEquals("IT", dto.country());
        assertEquals("https://example.com/job/1", dto.externalUrl());
    }

    @Test
    void fromMeshNode_notJobType_throwsIAE() {
        MeshNode node = new MeshNode();
        node.nodeType = NodeType.PROJECT;

        assertThrows(IllegalArgumentException.class, () -> JobPostingDto.fromMeshNode(node));
    }

    @Test
    void fromMeshNode_noStructuredData_mapsNulls() {
        MeshNode node = new MeshNode();
        node.id = UUID.randomUUID();
        node.nodeType = NodeType.JOB;
        node.title = "Dev";
        node.description = "Desc";
        node.structuredData = null;
        node.createdAt = Instant.now();
        node.updatedAt = Instant.now();

        JobPostingDto dto = JobPostingDto.fromMeshNode(node);

        assertNull(dto.requirementsText());
        assertNull(dto.workMode());
        assertNull(dto.employmentType());
        assertNull(dto.externalUrl());
    }

    @Test
    void fromMeshNode_noTags_fallsBackToStructuredData() {
        MeshNode node = new MeshNode();
        node.id = UUID.randomUUID();
        node.nodeType = NodeType.JOB;
        node.title = "Dev";
        node.description = "Desc";
        node.tags = null;
        node.structuredData = Map.of("skills_required", List.of("Go", "Rust"));
        node.createdAt = Instant.now();

        JobPostingDto dto = JobPostingDto.fromMeshNode(node);

        assertEquals(List.of("Go", "Rust"), dto.skillsRequired());
    }

    @Test
    void fromMeshNode_invalidWorkMode_returnsNull() {
        MeshNode node = new MeshNode();
        node.id = UUID.randomUUID();
        node.nodeType = NodeType.JOB;
        node.title = "Dev";
        node.description = "Desc";
        node.structuredData = Map.of("work_mode", "INVALID_MODE");
        node.createdAt = Instant.now();

        JobPostingDto dto = JobPostingDto.fromMeshNode(node);

        assertNull(dto.workMode());
    }

    @Test
    void fromNodeDto_validJob_maps() {
        NodeDto nodeDto = new NodeDto(
                UUID.randomUUID(), NodeType.JOB, "Role", "Desc",
                List.of("Java"), Map.of("work_mode", "HYBRID"),
                "US", Instant.now(), Instant.now(), null);

        JobPostingDto dto = JobPostingDto.fromNodeDto(nodeDto);

        assertEquals("Role", dto.title());
        assertEquals(WorkMode.HYBRID, dto.workMode());
    }

    @Test
    void fromNodeDto_notJobType_throwsIAE() {
        NodeDto nodeDto = new NodeDto(
                UUID.randomUUID(), NodeType.EVENT, "Event", "Desc",
                null, null, null, Instant.now(), null, null);

        assertThrows(IllegalArgumentException.class, () -> JobPostingDto.fromNodeDto(nodeDto));
    }

    @Test
    void fromMeshNode_emptyTags_fallsBackToStructuredData() {
        MeshNode node = new MeshNode();
        node.id = UUID.randomUUID();
        node.nodeType = NodeType.JOB;
        node.title = "Dev";
        node.description = "Desc";
        node.tags = List.of();
        node.structuredData = Map.of("skills_required", List.of("Python"));
        node.createdAt = Instant.now();

        JobPostingDto dto = JobPostingDto.fromMeshNode(node);

        assertEquals(List.of("Python"), dto.skillsRequired());
    }
}
