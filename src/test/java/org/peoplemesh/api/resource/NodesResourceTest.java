package org.peoplemesh.api.resource;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.api.error.ProblemDetail;
import org.peoplemesh.domain.dto.NodeDto;
import org.peoplemesh.domain.dto.NodePayload;
import org.peoplemesh.domain.dto.ProfileSchema;
import org.peoplemesh.domain.dto.SkillAssessmentDto;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.service.CurrentUserService;
import org.peoplemesh.service.NodeService;
import org.peoplemesh.service.ProfileService;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodesResourceTest {

    @Mock
    CurrentUserService currentUserService;
    @Mock
    NodeService nodeService;
    @Mock
    ProfileService profileService;

    @InjectMocks
    NodesResource resource;

    @Test
    void listMyNodes_returns200() {
        UUID userId = UUID.randomUUID();
        when(currentUserService.resolveUserId()).thenReturn(userId);
        when(nodeService.listByCreatorFiltered(userId, "JOB")).thenReturn(List.of(
                new NodeDto(
                        UUID.randomUUID(),
                        NodeType.JOB,
                        "Title",
                        "Desc",
                        List.of(),
                        Map.of(),
                        "IT",
                        Instant.now(),
                        Instant.now())
        ));

        Response response = resource.listMyNodes("JOB");

        assertEquals(200, response.getStatus());
    }

    @Test
    void getNode_notFound_returns404() {
        UUID userId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        when(currentUserService.resolveUserId()).thenReturn(userId);
        when(nodeService.getNode(nodeId)).thenReturn(Optional.empty());
        when(nodeService.getNodeForCreator(nodeId, userId)).thenReturn(Optional.empty());

        Response response = resource.getNode(nodeId);

        assertEquals(404, response.getStatus());
    }

    @Test
    void createNode_returns201() {
        UUID userId = UUID.randomUUID();
        NodePayload payload = new NodePayload(NodeType.JOB, "Title", "Desc", List.of("java"), Map.of(), "IT");
        NodeDto created = new NodeDto(
                UUID.randomUUID(),
                NodeType.JOB,
                "Title",
                "Desc",
                List.of("java"),
                Map.of(),
                "IT",
                Instant.now(),
                Instant.now());
        when(currentUserService.resolveUserId()).thenReturn(userId);
        when(nodeService.createNode(userId, payload)).thenReturn(created);

        Response response = resource.createNode(payload);

        assertEquals(201, response.getStatus());
        assertEquals(created, response.getEntity());
    }

    @Test
    void getNode_publicNodeFound_returns200WithoutCreatorFallback() {
        UUID userId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        NodeDto node = new NodeDto(
                nodeId,
                NodeType.PROJECT,
                "Public",
                "Desc",
                List.of(),
                Map.of(),
                null,
                Instant.now(),
                Instant.now());
        when(currentUserService.resolveUserId()).thenReturn(userId);
        when(nodeService.getNode(nodeId)).thenReturn(Optional.of(node));

        Response response = resource.getNode(nodeId);

        assertEquals(200, response.getStatus());
        assertEquals(node, response.getEntity());
        verify(nodeService).getNode(nodeId);
    }

    @Test
    void getNode_creatorNodeFound_returns200() {
        UUID userId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        NodeDto node = new NodeDto(
                nodeId,
                NodeType.JOB,
                "Mine",
                "Desc",
                List.of(),
                Map.of(),
                null,
                Instant.now(),
                Instant.now());
        when(currentUserService.resolveUserId()).thenReturn(userId);
        when(nodeService.getNode(nodeId)).thenReturn(Optional.empty());
        when(nodeService.getNodeForCreator(nodeId, userId)).thenReturn(Optional.of(node));

        Response response = resource.getNode(nodeId);

        assertEquals(200, response.getStatus());
        assertEquals(node, response.getEntity());
    }

    @Test
    void getNodeSkills_returns200WithSkills() {
        UUID userId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        UUID catalogId = UUID.randomUUID();
        List<SkillAssessmentDto> expected = List.of(SkillAssessmentDto.forInput(UUID.randomUUID(), 3, true));
        when(currentUserService.resolveUserId()).thenReturn(userId);
        when(nodeService.getNodeSkillsForUser(userId, nodeId, catalogId)).thenReturn(expected);

        Response response = resource.getNodeSkills(nodeId, catalogId);

        assertEquals(200, response.getStatus());
        assertEquals(expected, response.getEntity());
    }

    @Test
    void getNodeProfile_found_returns200() {
        UUID nodeId = UUID.randomUUID();
        ProfileSchema schema = new ProfileSchema(
                null,
                null,
                null,
                new ProfileSchema.ProfessionalInfo(List.of("Engineer"), null, null, List.of("Java"), null, null, null, null, null),
                null,
                null,
                null,
                null,
                null,
                null);
        when(profileService.getPublicProfile(nodeId)).thenReturn(Optional.of(schema));

        Response response = resource.getNodeProfile(nodeId);

        assertEquals(200, response.getStatus());
        assertEquals(schema, response.getEntity());
    }

    @Test
    void getNodeProfile_missing_returns404Problem() {
        UUID nodeId = UUID.randomUUID();
        when(profileService.getPublicProfile(nodeId)).thenReturn(Optional.empty());

        Response response = resource.getNodeProfile(nodeId);

        assertEquals(404, response.getStatus());
        ProblemDetail detail = assertInstanceOf(ProblemDetail.class, response.getEntity());
        assertEquals("Profile not found or not published", detail.detail());
    }

    @Test
    void updateNode_found_returns200() {
        UUID userId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        NodePayload payload = new NodePayload(NodeType.JOB, "Updated", "Desc", List.of(), Map.of(), null);
        NodeDto updated = new NodeDto(
                nodeId,
                NodeType.JOB,
                "Updated",
                "Desc",
                List.of(),
                Map.of(),
                null,
                Instant.now(),
                Instant.now());
        when(currentUserService.resolveUserId()).thenReturn(userId);
        when(nodeService.updateNode(userId, nodeId, payload)).thenReturn(Optional.of(updated));

        Response response = resource.updateNode(nodeId, payload);

        assertEquals(200, response.getStatus());
        assertEquals(updated, response.getEntity());
    }

    @Test
    void updateNode_missing_returns404() {
        UUID userId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        NodePayload payload = new NodePayload(NodeType.JOB, "Updated", "Desc", List.of(), Map.of(), null);
        when(currentUserService.resolveUserId()).thenReturn(userId);
        when(nodeService.updateNode(userId, nodeId, payload)).thenReturn(Optional.empty());

        Response response = resource.updateNode(nodeId, payload);

        assertEquals(404, response.getStatus());
        ProblemDetail detail = assertInstanceOf(ProblemDetail.class, response.getEntity());
        assertEquals("Node not found", detail.detail());
    }
}
