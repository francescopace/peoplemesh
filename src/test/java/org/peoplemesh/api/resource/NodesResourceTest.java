package org.peoplemesh.api.resource;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.api.error.ProblemDetail;
import org.peoplemesh.domain.dto.NodeDto;
import org.peoplemesh.domain.dto.ProfileSchema;
import org.peoplemesh.service.NodeService;
import org.peoplemesh.service.ProfileService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodesResourceTest {

    @Mock
    NodeService nodeService;
    @Mock
    ProfileService profileService;
    @InjectMocks
    NodesResource resource;

    @Test
    void getNode_notFound_returns404() {
        UUID nodeId = UUID.randomUUID();
        when(nodeService.getNode(nodeId, false)).thenReturn(Optional.empty());

        Response response = resource.getNode(nodeId, false);

        assertEquals(404, response.getStatus());
    }

    @Test
    void getNode_found_returns200() {
        UUID nodeId = UUID.randomUUID();
        NodeDto node = new NodeDto(
                nodeId,
                org.peoplemesh.domain.enums.NodeType.PROJECT,
                "Public",
                "Desc",
                List.of(),
                java.util.Map.of(),
                null,
                java.time.Instant.now(),
                java.time.Instant.now(),
                null);
        when(nodeService.getNode(nodeId, false)).thenReturn(Optional.of(node));

        Response response = resource.getNode(nodeId, false);

        assertEquals(200, response.getStatus());
        assertEquals(node, response.getEntity());
    }

    @Test
    void getNode_includeEmbedding_true_passesFlagToService() {
        UUID nodeId = UUID.randomUUID();
        NodeDto node = new NodeDto(
                nodeId,
                org.peoplemesh.domain.enums.NodeType.PROJECT,
                "Public",
                "Desc",
                List.of(),
                java.util.Map.of(),
                null,
                java.time.Instant.now(),
                java.time.Instant.now(),
                new float[]{0.1f, 0.2f}
        );
        when(nodeService.getNode(nodeId, true)).thenReturn(Optional.of(node));

        Response response = resource.getNode(nodeId, true);

        assertEquals(200, response.getStatus());
        assertEquals(node, response.getEntity());
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

}
