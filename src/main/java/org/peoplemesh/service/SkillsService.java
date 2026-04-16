package org.peoplemesh.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.peoplemesh.domain.dto.CatalogCreateRequest;
import org.peoplemesh.domain.dto.SkillCatalogDto;
import org.peoplemesh.domain.dto.SkillDefinitionDto;
import org.peoplemesh.domain.exception.ForbiddenBusinessException;
import org.peoplemesh.domain.model.SkillCatalog;
import org.peoplemesh.domain.model.SkillDefinition;
import org.peoplemesh.mapper.SkillCatalogMapper;
import org.peoplemesh.repository.SkillDefinitionRepository;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class SkillsService {

    @Inject
    SkillCatalogService catalogService;

    @Inject
    SkillDefinitionRepository skillDefinitionRepository;

    @Inject
    EntitlementService entitlementService;

    @Inject
    SkillCatalogMapper skillCatalogMapper;

    public List<SkillCatalogDto> listCatalogs() {
        return catalogService.listCatalogs().stream()
                .map(c -> skillCatalogMapper.toCatalogDto(c, SkillDefinition.countByCatalog(c.id)))
                .toList();
    }

    public SkillCatalogDto createCatalog(UUID userId, CatalogCreateRequest body) {
        ensureCanManageSkills(userId);
        Map<String, Object> levelScale = body.levelScale();
        if (levelScale == null || levelScale.isEmpty()) {
            levelScale = defaultLevelScale();
        }
        SkillCatalog catalog = catalogService.createCatalog(body.name(), body.description(), levelScale, body.source());
        return skillCatalogMapper.toCatalogDto(catalog, SkillDefinition.countByCatalog(catalog.id));
    }

    public SkillCatalogDto updateCatalog(UUID userId, UUID catalogId, CatalogCreateRequest body) {
        ensureCanManageSkills(userId);
        SkillCatalog updated = catalogService.updateCatalog(
                catalogId, body.name(), body.description(), body.levelScale(), body.source());
        return skillCatalogMapper.toCatalogDto(updated, SkillDefinition.countByCatalog(updated.id));
    }

    public SkillCatalogDto getCatalog(UUID catalogId) {
        return catalogService.getCatalog(catalogId)
                .map(c -> skillCatalogMapper.toCatalogDto(c, SkillDefinition.countByCatalog(c.id)))
                .orElse(null);
    }

    public int importCsv(UUID userId, UUID catalogId, InputStream csvStream) throws IOException {
        ensureCanManageSkills(userId);
        int count = catalogService.importFromCsv(catalogId, csvStream);
        catalogService.generateEmbeddings(catalogId);
        return count;
    }

    public List<SkillDefinitionDto> listSkills(UUID catalogId, String category, int page, int size) {
        int sanitizedSize = Math.min(size, 200);
        List<SkillDefinition> skills = catalogService.listSkills(catalogId, category, page, sanitizedSize);
        return skills.stream().map(skillCatalogMapper::toDefinitionDto).toList();
    }

    public List<String> listCategories(UUID catalogId) {
        return skillDefinitionRepository.listCategories(catalogId);
    }

    public void deleteCatalog(UUID userId, UUID catalogId) {
        ensureCanManageSkills(userId);
        catalogService.deleteCatalog(catalogId);
    }

    private void ensureCanManageSkills(UUID userId) {
        if (!entitlementService.canManageSkills(userId)) {
            throw new ForbiddenBusinessException("Skill catalog management requires can_manage_skills entitlement");
        }
    }

    private static Map<String, Object> defaultLevelScale() {
        Map<String, Object> scale = new LinkedHashMap<>();
        scale.put("0", "None");
        scale.put("1", "Aware");
        scale.put("2", "Beginner");
        scale.put("3", "Practitioner");
        scale.put("4", "Advanced");
        scale.put("5", "Expert");
        return scale;
    }
}
