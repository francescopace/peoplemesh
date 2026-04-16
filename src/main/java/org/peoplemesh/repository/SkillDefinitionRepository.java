package org.peoplemesh.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.peoplemesh.domain.model.SkillDefinition;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class SkillDefinitionRepository {

    @Inject
    EntityManager em;

    public List<SkillDefinition> findByCatalog(UUID catalogId) {
        return SkillDefinition.findByCatalog(catalogId);
    }

    public Optional<SkillDefinition> findByCatalogAndName(UUID catalogId, String name) {
        return SkillDefinition.findByCatalogAndName(catalogId, name);
    }

    public List<SkillDefinition> findByIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return SkillDefinition.list("id in ?1", ids);
    }

    public Map<UUID, SkillDefinition> findMapByIds(List<UUID> ids) {
        return findByIds(ids).stream().collect(Collectors.toMap(sd -> sd.id, sd -> sd));
    }

    public List<String> listCategories(UUID catalogId) {
        return SkillDefinition.listCategories(catalogId);
    }

    public List<SkillDefinition> listSkills(UUID catalogId, String category, int page, int size) {
        if (category != null && !category.isBlank()) {
            return em.createQuery(
                            "FROM SkillDefinition d WHERE d.catalogId = ?1 AND d.category = ?2 ORDER BY d.name",
                            SkillDefinition.class)
                    .setParameter(1, catalogId)
                    .setParameter(2, category)
                    .setFirstResult(page * size)
                    .setMaxResults(size)
                    .getResultList();
        }
        return em.createQuery(
                        "FROM SkillDefinition d WHERE d.catalogId = ?1 ORDER BY d.category, d.name",
                        SkillDefinition.class)
                .setParameter(1, catalogId)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();
    }
}
