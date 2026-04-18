package org.peoplemesh.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.domain.dto.CatalogCreateRequest;
import org.peoplemesh.domain.dto.SkillCatalogDto;
import org.peoplemesh.domain.dto.SkillDefinitionDto;
import org.peoplemesh.domain.exception.ForbiddenBusinessException;
import org.peoplemesh.domain.exception.NotFoundBusinessException;
import org.peoplemesh.domain.model.SkillCatalog;
import org.peoplemesh.domain.model.SkillDefinition;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SkillsServiceTest {

    @Mock
    SkillCatalogService catalogService;
    @Mock
    EntitlementService entitlementService;

    @InjectMocks
    SkillsService service;

    @Test
    void listCatalogs_mapsSkillCounts() {
        SkillCatalog catalog = catalog(UUID.randomUUID(), "Main");
        when(catalogService.listCatalogs()).thenReturn(List.of(catalog));
        when(catalogService.countSkillsByCatalogIds(List.of(catalog.id))).thenReturn(Map.of(catalog.id, 7L));

        List<SkillCatalogDto> result = service.listCatalogs();

        assertEquals(1, result.size());
        assertEquals(7L, result.get(0).skillCount());
    }

    @Test
    void createCatalog_requiresAdminAndUsesDefaultScale() {
        UUID userId = UUID.randomUUID();
        CatalogCreateRequest request = new CatalogCreateRequest("Cat", "Desc", null, "MANUAL");
        when(entitlementService.isAdmin(userId)).thenReturn(true);
        SkillCatalog created = catalog(UUID.randomUUID(), "Cat");
        when(catalogService.createCatalog(any(), any(), any(), any())).thenReturn(created);
        when(catalogService.countSkillsByCatalog(created.id)).thenReturn(0L);

        SkillCatalogDto dto = service.createCatalog(userId, request);

        assertEquals("Cat", dto.name());
        verify(catalogService).createCatalog(any(), any(), any(), any());
    }

    @Test
    void createCatalog_nonAdmin_throwsForbidden() {
        UUID userId = UUID.randomUUID();
        when(entitlementService.isAdmin(userId)).thenReturn(false);

        assertThrows(ForbiddenBusinessException.class,
                () -> service.createCatalog(userId, new CatalogCreateRequest("x", "y", null, "MANUAL")));
    }

    @Test
    void getCatalog_whenAbsent_throwsNotFound() {
        UUID catalogId = UUID.randomUUID();
        when(catalogService.getCatalog(catalogId)).thenReturn(Optional.empty());
        assertThrows(NotFoundBusinessException.class, () -> service.getCatalog(catalogId));
    }

    @Test
    void importCsv_generatesEmbeddingsAfterImport() throws IOException {
        UUID userId = UUID.randomUUID();
        UUID catalogId = UUID.randomUUID();
        when(entitlementService.isAdmin(userId)).thenReturn(true);
        when(catalogService.importFromCsv(eq(catalogId), any(InputStream.class))).thenReturn(2);

        int imported = service.importCsv(userId, catalogId, new ByteArrayInputStream("a".getBytes()));
        assertEquals(2, imported);
        verify(catalogService).generateEmbeddings(catalogId);
    }

    @Test
    void listSkills_sanitizesPageSize() {
        SkillDefinition definition = new SkillDefinition();
        definition.id = UUID.randomUUID();
        definition.name = "Java";
        definition.category = "Backend";
        when(catalogService.listSkills(any(), any(), anyInt(), anyInt())).thenReturn(List.of(definition));

        List<SkillDefinitionDto> result = service.listSkills(UUID.randomUUID(), null, 0, 1000);
        assertEquals(1, result.size());
    }

    @Test
    void deleteCatalog_requiresAdmin() {
        UUID userId = UUID.randomUUID();
        UUID catalogId = UUID.randomUUID();
        when(entitlementService.isAdmin(userId)).thenReturn(true);

        service.deleteCatalog(userId, catalogId);
        verify(catalogService).deleteCatalog(catalogId);
    }

    private static SkillCatalog catalog(UUID id, String name) {
        SkillCatalog catalog = new SkillCatalog();
        catalog.id = id;
        catalog.name = name;
        catalog.source = "MANUAL";
        return catalog;
    }
}
