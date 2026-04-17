package org.peoplemesh.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.domain.model.SkillCatalog;
import org.peoplemesh.domain.model.SkillDefinition;
import org.peoplemesh.repository.SkillCatalogRepository;
import org.peoplemesh.repository.SkillDefinitionRepository;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkillCatalogServiceTest {

    @Mock EmbeddingService embeddingService;
    @Mock SkillDefinitionRepository skillDefinitionRepository;
    @Mock SkillCatalogRepository skillCatalogRepository;

    @InjectMocks
    SkillCatalogService service;

    @Test
    void importFromCsv_catalogNotFound_throws() {
        UUID id = UUID.randomUUID();
        when(skillCatalogRepository.findById(id)).thenReturn(Optional.empty());
        assertThrows(jakarta.ws.rs.NotFoundException.class,
                () -> service.importFromCsv(id, new ByteArrayInputStream(new byte[0])));
    }

    @Test
    void importFromCsv_emptyFile_returnsZero() throws IOException {
        UUID id = UUID.randomUUID();
        SkillCatalog catalog = new SkillCatalog();
        catalog.name = "test";
        when(skillCatalogRepository.findById(id)).thenReturn(Optional.of(catalog));
        int count = service.importFromCsv(id, csvStream(""));
        assertEquals(0, count);
    }

    @Test
    void importFromCsv_headerOnly_returnsZero() throws IOException {
        UUID id = UUID.randomUUID();
        SkillCatalog catalog = new SkillCatalog();
        catalog.name = "test";
        when(skillCatalogRepository.findById(id)).thenReturn(Optional.of(catalog));
        when(skillDefinitionRepository.findByCatalog(id)).thenReturn(List.of());
        int count = service.importFromCsv(id, csvStream("category,name,lxp\n"));
        assertEquals(0, count);
    }

    @Test
    void importFromCsv_updatesExistingSkill() throws IOException {
        UUID id = UUID.randomUUID();
        SkillCatalog catalog = new SkillCatalog();
        catalog.name = "test";
        when(skillCatalogRepository.findById(id)).thenReturn(Optional.of(catalog));
        SkillDefinition existing = new SkillDefinition();
        existing.name = "Java";
        existing.category = "OldCategory";
        when(skillDefinitionRepository.findByCatalog(id)).thenReturn(List.of(existing));
        String csv = "category,name\nBackend,Java";
        int count = service.importFromCsv(id, csvStream(csv));
        assertEquals(0, count);
        assertEquals("Backend", existing.category);
    }

    @Test
    void importFromCsv_escoHeader_updatesExistingSkill() throws IOException {
        UUID id = UUID.randomUUID();
        SkillCatalog catalog = new SkillCatalog();
        catalog.name = "test";
        when(skillCatalogRepository.findById(id)).thenReturn(Optional.of(catalog));
        SkillDefinition existing = new SkillDefinition();
        existing.name = "industrial software";
        existing.category = "OldCategory";
        when(skillDefinitionRepository.findByCatalog(id)).thenReturn(List.of(existing));
        String csv = """
                uri,title,preferred_label_en,skill_type,reuse_level
                http://data.europa.eu/esco/skill/41ec47dd-08b3-464a-9c45-c706f3e74467,industrial software,industrial software,knowledge,cross-sector
                """;
        int count = service.importFromCsv(id, csvStream(csv));
        assertEquals(0, count);
        assertEquals("Knowledge", existing.category);
        assertEquals("cross-sector", existing.lxpRecommendation);
    }

    @Test
    void importFromCsv_skillsBaseCategoryNameHeader_updatesExistingSkill() throws IOException {
        UUID id = UUID.randomUUID();
        SkillCatalog catalog = new SkillCatalog();
        catalog.name = "test";
        when(skillCatalogRepository.findById(id)).thenReturn(Optional.of(catalog));
        SkillDefinition existing = new SkillDefinition();
        existing.name = "Java";
        existing.category = "OldCategory";
        when(skillDefinitionRepository.findByCatalog(id)).thenReturn(List.of(existing));
        String csv = """
                "Category name",Name,"LXP Recommendation\t"
                "Agile Project Management","Java",
                """;
        int count = service.importFromCsv(id, csvStream(csv));
        assertEquals(0, count);
        assertEquals("Agile Project Management", existing.category);
        assertNull(existing.lxpRecommendation);
    }

    @Test
    void importFromCsv_skillsBaseCategoryNameHeaderWithBom_updatesExistingSkill() throws IOException {
        UUID id = UUID.randomUUID();
        SkillCatalog catalog = new SkillCatalog();
        catalog.name = "test";
        when(skillCatalogRepository.findById(id)).thenReturn(Optional.of(catalog));
        SkillDefinition existing = new SkillDefinition();
        existing.name = "Java";
        existing.category = "OldCategory";
        when(skillDefinitionRepository.findByCatalog(id)).thenReturn(List.of(existing));
        String csv = """
                \uFEFF"Category name",Name,"LXP Recommendation\t"
                "Agile Project Management","Java",
                """;
        int count = service.importFromCsv(id, csvStream(csv));
        assertEquals(0, count);
        assertEquals("Agile Project Management", existing.category);
        assertNull(existing.lxpRecommendation);
    }

    @Test
    void importFromCsv_unsupportedHeader_throws() {
        UUID id = UUID.randomUUID();
        SkillCatalog catalog = new SkillCatalog();
        catalog.name = "test";
        when(skillCatalogRepository.findById(id)).thenReturn(Optional.of(catalog));
        String csv = "foo,bar\nx,y\n";
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.importFromCsv(id, csvStream(csv)));
        assertTrue(ex.getMessage().contains("Unsupported skills CSV format"));
    }

    @Test
    void generateEmbeddings_skipsExisting() {
        UUID id = UUID.randomUUID();
        SkillDefinition withEmb = new SkillDefinition();
        withEmb.id = UUID.randomUUID();
        withEmb.name = "Java"; withEmb.category = "Backend"; withEmb.embedding = new float[]{1f};

        SkillDefinition withoutEmb = new SkillDefinition();
        withoutEmb.id = UUID.randomUUID();
        withoutEmb.name = "Python"; withoutEmb.category = "Backend";

        when(skillDefinitionRepository.findByCatalog(id)).thenReturn(List.of(withEmb, withoutEmb));
        when(embeddingService.generateEmbeddings(anyList())).thenReturn(List.of(new float[]{0.5f}));

        service.generateEmbeddings(id);

        verify(embeddingService, times(1)).generateEmbeddings(anyList());
        verify(skillDefinitionRepository, atLeastOnce()).upsert(withoutEmb);
        assertNotNull(withoutEmb.embedding);
    }

    @Test
    void generateEmbeddings_includesAliasesInText() {
        UUID id = UUID.randomUUID();
        SkillDefinition sd = new SkillDefinition();
        sd.id = UUID.randomUUID();
        sd.name = "JavaScript"; sd.category = "Frontend"; sd.aliases = List.of("JS");

        when(skillDefinitionRepository.findByCatalog(id)).thenReturn(List.of(sd));
        when(embeddingService.generateEmbeddings(anyList())).thenAnswer(inv -> List.of(new float[]{0.1f}));

        service.generateEmbeddings(id);

        verify(embeddingService).generateEmbeddings(argThat(list -> !list.isEmpty() && list.get(0).contains("JS")));
    }

    @Test
    void generateEmbeddings_failureDoesNotStop() {
        UUID id = UUID.randomUUID();
        SkillDefinition sd1 = new SkillDefinition(); sd1.id = UUID.randomUUID(); sd1.name = "A"; sd1.category = "Cat";
        SkillDefinition sd2 = new SkillDefinition(); sd2.id = UUID.randomUUID(); sd2.name = "B"; sd2.category = "Cat";

        when(skillDefinitionRepository.findByCatalog(id)).thenReturn(List.of(sd1, sd2));
        when(embeddingService.generateEmbeddings(anyList())).thenThrow(new RuntimeException("fail"));

        assertDoesNotThrow(() -> service.generateEmbeddings(id));
    }

    @Test
    void deleteCatalog_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(skillCatalogRepository.findById(id)).thenReturn(Optional.empty());
        assertThrows(jakarta.ws.rs.NotFoundException.class, () -> service.deleteCatalog(id));
    }

    @Test
    void listCatalogs_delegates() {
        when(skillCatalogRepository.findAllSorted()).thenReturn(Collections.emptyList());
        assertEquals(Collections.emptyList(), service.listCatalogs());
    }

    @Test
    void getCatalog_delegates() {
        UUID id = UUID.randomUUID();
        when(skillCatalogRepository.findById(id)).thenReturn(Optional.empty());
        assertTrue(service.getCatalog(id).isEmpty());
    }

    @Test
    void listSkills_withCategory_filtersAndPaginates() {
        UUID id = UUID.randomUUID();
        when(skillDefinitionRepository.listSkills(id, "Backend", 0, 10)).thenReturn(Collections.emptyList());

        service.listSkills(id, "Backend", 0, 10);

        verify(skillDefinitionRepository).listSkills(id, "Backend", 0, 10);
    }

    @Test
    void listSkills_noCategory_listsAll() {
        UUID id = UUID.randomUUID();
        when(skillDefinitionRepository.listSkills(id, null, 1, 20)).thenReturn(Collections.emptyList());

        service.listSkills(id, null, 1, 20);

        verify(skillDefinitionRepository).listSkills(id, null, 1, 20);
    }

    private static ByteArrayInputStream csvStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
