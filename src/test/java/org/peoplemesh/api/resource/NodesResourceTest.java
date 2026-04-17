package org.peoplemesh.api.resource;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.domain.dto.NodeDto;
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
}
