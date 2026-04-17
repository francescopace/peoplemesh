package org.peoplemesh.api.resource;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.domain.dto.SkillCatalogDto;
import org.peoplemesh.service.CurrentUserService;
import org.peoplemesh.service.SkillsService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void listCatalogs_returns200() {
        when(skillsService.listCatalogs()).thenReturn(List.of(
                new SkillCatalogDto(UUID.randomUUID(), "Catalog", "Desc", Map.of(), "manual", 0, null, null)
        ));

        Response response = resource.listCatalogs();

        assertEquals(200, response.getStatus());
    }

    @Test
    void deleteCatalog_callsService() {
        UUID userId = UUID.randomUUID();
        UUID catalogId = UUID.randomUUID();
        when(currentUserService.resolveUserId()).thenReturn(userId);

        Response response = resource.deleteCatalog(catalogId);

        assertEquals(204, response.getStatus());
        verify(skillsService).deleteCatalog(userId, catalogId);
    }
}
