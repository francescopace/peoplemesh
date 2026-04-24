package org.peoplemesh.api.resource;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.domain.dto.SkillDefinitionDto;
import org.peoplemesh.service.CurrentUserService;
import org.peoplemesh.service.SkillsService;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SkillsResourceTest {

    @Mock
    SkillsService skillsService;
    @Mock
    CurrentUserService currentUserService;

    @InjectMocks
    SkillsResource resource;

    @Test
    void listSkills_returns200() {
        when(skillsService.listSkills("ja", 0, 50)).thenReturn(List.of(
                new SkillDefinitionDto(UUID.randomUUID(), "java", List.of("Java"), 10, true)
        ));

        Response response = resource.listSkills("ja", 0, 50);

        assertEquals(200, response.getStatus());
    }

    @Test
    void cleanupUnused_callsService() {
        UUID userId = UUID.randomUUID();
        when(currentUserService.resolveUserId()).thenReturn(userId);
        when(skillsService.cleanupUnused(userId)).thenReturn(3);

        Response response = resource.cleanupUnused();

        assertEquals(200, response.getStatus());
        assertEquals(Map.of("deleted", 3), response.getEntity());
        verify(skillsService).cleanupUnused(userId);
    }

    @Test
    void importCsv_callsService() throws Exception {
        UUID userId = UUID.randomUUID();
        when(currentUserService.resolveUserId()).thenReturn(userId);
        when(skillsService.importCsv(eq(userId), any())).thenReturn(2);

        Response response = resource.importCsv(new ByteArrayInputStream("name\njava\n".getBytes()));

        assertEquals(200, response.getStatus());
        assertEquals(Map.of("imported", 2), response.getEntity());
        verify(skillsService).importCsv(eq(userId), any());
    }
}
