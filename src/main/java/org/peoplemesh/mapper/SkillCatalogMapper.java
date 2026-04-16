package org.peoplemesh.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import org.peoplemesh.domain.dto.SkillCatalogDto;
import org.peoplemesh.domain.dto.SkillDefinitionDto;
import org.peoplemesh.domain.model.SkillCatalog;
import org.peoplemesh.domain.model.SkillDefinition;

@ApplicationScoped
public class SkillCatalogMapper {

    public SkillCatalogDto toCatalogDto(SkillCatalog catalog, long skillCount) {
        return new SkillCatalogDto(
                catalog.id,
                catalog.name,
                catalog.description,
                catalog.levelScale,
                catalog.source,
                skillCount,
                catalog.createdAt != null ? catalog.createdAt.toString() : null,
                catalog.updatedAt != null ? catalog.updatedAt.toString() : null
        );
    }

    public SkillDefinitionDto toDefinitionDto(SkillDefinition definition) {
        return new SkillDefinitionDto(
                definition.id,
                definition.category,
                definition.name,
                definition.aliases,
                definition.lxpRecommendation,
                definition.embedding != null
        );
    }
}
