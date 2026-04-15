package org.peoplemesh.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.peoplemesh.domain.model.SkillCatalog;
import org.peoplemesh.domain.model.SkillDefinition;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

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
    @Mock EntityManager em;

    @InjectMocks
    SkillCatalogService service;

    @Test
    void importFromCsv_catalogNotFound_throws() {
        UUID id = UUID.randomUUID();
        try (var mocked = mockStatic(SkillCatalog.class)) {
            mocked.when(() -> SkillCatalog.findByIdOptional(id)).thenReturn(Optional.empty());

            assertThrows(jakarta.ws.rs.NotFoundException.class,
                    () -> service.importFromCsv(id, new ByteArrayInputStream(new byte[0])));
        }
    }

    @Test
    void importFromCsv_emptyFile_returnsZero() throws IOException {
        UUID id = UUID.randomUUID();
        try (var mocked = mockStatic(SkillCatalog.class)) {
            SkillCatalog catalog = new SkillCatalog();
            catalog.name = "test";
            mocked.when(() -> SkillCatalog.findByIdOptional(id)).thenReturn(Optional.of(catalog));

            int count = service.importFromCsv(id, csvStream(""));
            assertEquals(0, count);
        }
    }

    @Test
    void importFromCsv_headerOnly_returnsZero() throws IOException {
        UUID id = UUID.randomUUID();
        try (var mocked = mockStatic(SkillCatalog.class)) {
            SkillCatalog catalog = new SkillCatalog();
            catalog.name = "test";
            mocked.when(() -> SkillCatalog.findByIdOptional(id)).thenReturn(Optional.of(catalog));

            int count = service.importFromCsv(id, csvStream("category,name,lxp\n"));
            assertEquals(0, count);
        }
    }

    @Test
    void importFromCsv_updatesExistingSkill() throws IOException {
        UUID id = UUID.randomUUID();
        try (var catalogMock = mockStatic(SkillCatalog.class);
             var skillMock = mockStatic(SkillDefinition.class)) {

            SkillCatalog catalog = new SkillCatalog();
            catalog.name = "test";
            catalogMock.when(() -> SkillCatalog.findByIdOptional(id)).thenReturn(Optional.of(catalog));

            SkillDefinition existing = new SkillDefinition();
            existing.name = "Java";
            existing.category = "OldCategory";
            skillMock.when(() -> SkillDefinition.findByCatalogAndName(id, "Java"))
                    .thenReturn(Optional.of(existing));

            String csv = "category,name\nBackend,Java";
            int count = service.importFromCsv(id, csvStream(csv));

            assertEquals(0, count);
            assertEquals("Backend", existing.category);
        }
    }

    @Test
    void importFromCsv_escoHeader_updatesExistingSkill() throws IOException {
        UUID id = UUID.randomUUID();
        try (var catalogMock = mockStatic(SkillCatalog.class);
             var skillMock = mockStatic(SkillDefinition.class)) {

            SkillCatalog catalog = new SkillCatalog();
            catalog.name = "test";
            catalogMock.when(() -> SkillCatalog.findByIdOptional(id)).thenReturn(Optional.of(catalog));

            SkillDefinition existing = new SkillDefinition();
            existing.name = "industrial software";
            existing.category = "OldCategory";
            skillMock.when(() -> SkillDefinition.findByCatalogAndName(id, "industrial software"))
                    .thenReturn(Optional.of(existing));

            String csv = """
                    uri,title,preferred_label_en,skill_type,reuse_level
                    http://data.europa.eu/esco/skill/41ec47dd-08b3-464a-9c45-c706f3e74467,industrial software,industrial software,knowledge,cross-sector
                    """;
            int count = service.importFromCsv(id, csvStream(csv));

            assertEquals(0, count);
            assertEquals("Knowledge", existing.category);
            assertEquals("cross-sector", existing.lxpRecommendation);
        }
    }

    @Test
    void importFromCsv_skillsBaseCategoryNameHeader_updatesExistingSkill() throws IOException {
        UUID id = UUID.randomUUID();
        try (var catalogMock = mockStatic(SkillCatalog.class);
             var skillMock = mockStatic(SkillDefinition.class)) {

            SkillCatalog catalog = new SkillCatalog();
            catalog.name = "test";
            catalogMock.when(() -> SkillCatalog.findByIdOptional(id)).thenReturn(Optional.of(catalog));

            SkillDefinition existing = new SkillDefinition();
            existing.name = "Java";
            existing.category = "OldCategory";
            skillMock.when(() -> SkillDefinition.findByCatalogAndName(id, "Java"))
                    .thenReturn(Optional.of(existing));

            String csv = """
                    "Category name",Name,"LXP Recommendation\t"
                    "Agile Project Management","Java",
                    """;
            int count = service.importFromCsv(id, csvStream(csv));

            assertEquals(0, count);
            assertEquals("Agile Project Management", existing.category);
            assertNull(existing.lxpRecommendation);
        }
    }

    @Test
    void importFromCsv_skillsBaseCategoryNameHeaderWithBom_updatesExistingSkill() throws IOException {
        UUID id = UUID.randomUUID();
        try (var catalogMock = mockStatic(SkillCatalog.class);
             var skillMock = mockStatic(SkillDefinition.class)) {

            SkillCatalog catalog = new SkillCatalog();
            catalog.name = "test";
            catalogMock.when(() -> SkillCatalog.findByIdOptional(id)).thenReturn(Optional.of(catalog));

            SkillDefinition existing = new SkillDefinition();
            existing.name = "Java";
            existing.category = "OldCategory";
            skillMock.when(() -> SkillDefinition.findByCatalogAndName(id, "Java"))
                    .thenReturn(Optional.of(existing));

            String csv = """
                    \uFEFF"Category name",Name,"LXP Recommendation\t"
                    "Agile Project Management","Java",
                    """;
            int count = service.importFromCsv(id, csvStream(csv));

            assertEquals(0, count);
            assertEquals("Agile Project Management", existing.category);
            assertNull(existing.lxpRecommendation);
        }
    }

    @Test
    void importFromCsv_unsupportedHeader_throws() {
        UUID id = UUID.randomUUID();
        try (var mocked = mockStatic(SkillCatalog.class)) {
            SkillCatalog catalog = new SkillCatalog();
            catalog.name = "test";
            mocked.when(() -> SkillCatalog.findByIdOptional(id)).thenReturn(Optional.of(catalog));

            String csv = "foo,bar\nx,y\n";
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.importFromCsv(id, csvStream(csv)));
            assertTrue(ex.getMessage().contains("Unsupported skills CSV format"));
        }
    }

    @Test
    void generateEmbeddings_skipsExisting() {
        UUID id = UUID.randomUUID();
        try (var mocked = mockStatic(SkillDefinition.class)) {
            SkillDefinition withEmb = new SkillDefinition();
            withEmb.name = "Java"; withEmb.category = "Backend"; withEmb.embedding = new float[]{1f};

            SkillDefinition withoutEmb = new SkillDefinition();
            withoutEmb.name = "Python"; withoutEmb.category = "Backend";

            mocked.when(() -> SkillDefinition.findByCatalog(id)).thenReturn(List.of(withEmb, withoutEmb));
            when(embeddingService.generateEmbedding(anyString())).thenReturn(new float[]{0.5f});

            service.generateEmbeddings(id);

            verify(embeddingService, times(1)).generateEmbedding(anyString());
            assertNotNull(withoutEmb.embedding);
        }
    }

    @Test
    void generateEmbeddings_includesAliasesInText() {
        UUID id = UUID.randomUUID();
        try (var mocked = mockStatic(SkillDefinition.class)) {
            SkillDefinition sd = new SkillDefinition();
            sd.name = "JavaScript"; sd.category = "Frontend"; sd.aliases = List.of("JS");

            mocked.when(() -> SkillDefinition.findByCatalog(id)).thenReturn(List.of(sd));
            when(embeddingService.generateEmbedding(contains("JS"))).thenReturn(new float[]{0.1f});

            service.generateEmbeddings(id);

            verify(embeddingService).generateEmbedding("Frontend: JavaScript (JS)");
        }
    }

    @Test
    void generateEmbeddings_failureDoesNotStop() {
        UUID id = UUID.randomUUID();
        try (var mocked = mockStatic(SkillDefinition.class)) {
            SkillDefinition sd1 = new SkillDefinition(); sd1.name = "A"; sd1.category = "Cat";
            SkillDefinition sd2 = new SkillDefinition(); sd2.name = "B"; sd2.category = "Cat";

            mocked.when(() -> SkillDefinition.findByCatalog(id)).thenReturn(List.of(sd1, sd2));
            when(embeddingService.generateEmbedding(contains("A"))).thenThrow(new RuntimeException("fail"));
            when(embeddingService.generateEmbedding(contains("B"))).thenReturn(new float[]{0.1f});

            assertDoesNotThrow(() -> service.generateEmbeddings(id));
            assertNotNull(sd2.embedding);
        }
    }

    @Test
    void deleteCatalog_notFound_throws() {
        UUID id = UUID.randomUUID();
        try (var mocked = mockStatic(SkillCatalog.class)) {
            mocked.when(() -> SkillCatalog.findByIdOptional(id)).thenReturn(Optional.empty());

            assertThrows(jakarta.ws.rs.NotFoundException.class, () -> service.deleteCatalog(id));
        }
    }

    @Test
    void listCatalogs_delegates() {
        try (var mocked = mockStatic(SkillCatalog.class)) {
            mocked.when(SkillCatalog::findAllSorted).thenReturn(Collections.emptyList());

            assertEquals(Collections.emptyList(), service.listCatalogs());
        }
    }

    @Test
    void getCatalog_delegates() {
        UUID id = UUID.randomUUID();
        try (var mocked = mockStatic(SkillCatalog.class)) {
            mocked.when(() -> SkillCatalog.findByIdOptional(id)).thenReturn(Optional.empty());

            assertTrue(service.getCatalog(id).isEmpty());
        }
    }

    @Test
    void listSkills_withCategory_filtersAndPaginates() {
        UUID id = UUID.randomUUID();
        @SuppressWarnings("unchecked")
        TypedQuery<SkillDefinition> query = mock(TypedQuery.class);
        doReturn(query).when(em).createQuery(contains("category"), eq(SkillDefinition.class));
        when(query.setParameter(anyInt(), any())).thenReturn(query);
        when(query.setFirstResult(anyInt())).thenReturn(query);
        when(query.setMaxResults(anyInt())).thenReturn(query);
        when(query.getResultList()).thenReturn(Collections.emptyList());

        service.listSkills(id, "Backend", 0, 10);

        verify(query).setParameter(2, "Backend");
        verify(query).setFirstResult(0);
        verify(query).setMaxResults(10);
    }

    @Test
    void listSkills_noCategory_listsAll() {
        UUID id = UUID.randomUUID();
        @SuppressWarnings("unchecked")
        TypedQuery<SkillDefinition> query = mock(TypedQuery.class);
        doReturn(query).when(em).createQuery(contains("ORDER BY"), eq(SkillDefinition.class));
        when(query.setParameter(anyInt(), any())).thenReturn(query);
        when(query.setFirstResult(anyInt())).thenReturn(query);
        when(query.setMaxResults(anyInt())).thenReturn(query);
        when(query.getResultList()).thenReturn(Collections.emptyList());

        service.listSkills(id, null, 1, 20);

        verify(query).setFirstResult(20);
    }

    private static ByteArrayInputStream csvStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
