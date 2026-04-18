package org.peoplemesh.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.peoplemesh.domain.model.SkillDefinition;
import org.peoplemesh.util.VectorSqlUtils;

import java.util.Collections;
import java.util.HashMap;
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
        return em.createQuery("FROM SkillDefinition d WHERE d.catalogId = :catalogId", SkillDefinition.class)
                .setParameter("catalogId", catalogId)
                .getResultList();
    }

    public List<SkillDefinition> findAll() {
        return em.createQuery("FROM SkillDefinition d ORDER BY d.name", SkillDefinition.class)
                .getResultList();
    }

    public long countAll() {
        return em.createQuery("SELECT COUNT(d) FROM SkillDefinition d", Long.class)
                .getSingleResult();
    }

    public long countByCatalog(UUID catalogId) {
        return em.createQuery("SELECT COUNT(d) FROM SkillDefinition d WHERE d.catalogId = :catalogId", Long.class)
                .setParameter("catalogId", catalogId)
                .getSingleResult();
    }

    public Optional<SkillDefinition> findByCatalogAndName(UUID catalogId, String name) {
        if (name == null) {
            return Optional.empty();
        }
        return em.createQuery(
                        "FROM SkillDefinition d WHERE d.catalogId = :catalogId AND LOWER(d.name) = :name",
                        SkillDefinition.class)
                .setParameter("catalogId", catalogId)
                .setParameter("name", name.toLowerCase())
                .getResultStream()
                .findFirst();
    }

    public List<SkillDefinition> findByIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return em.createQuery("FROM SkillDefinition d WHERE d.id in :ids", SkillDefinition.class)
                .setParameter("ids", ids)
                .getResultList();
    }

    public Map<UUID, SkillDefinition> findMapByIds(List<UUID> ids) {
        return findByIds(ids).stream().collect(Collectors.toMap(sd -> sd.id, sd -> sd));
    }

    public void upsert(SkillDefinition skillDefinition) {
        if (skillDefinition.id == null) {
            em.persist(skillDefinition);
        } else {
            em.merge(skillDefinition);
        }
    }

    public Map<UUID, Long> countByCatalogIds(List<UUID> catalogIds) {
        if (catalogIds == null || catalogIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Object[]> rows = em.createQuery(
                        "SELECT d.catalogId, COUNT(d) " +
                                "FROM SkillDefinition d " +
                                "WHERE d.catalogId IN ?1 " +
                                "GROUP BY d.catalogId",
                        Object[].class)
                .setParameter(1, catalogIds)
                .getResultList();
        Map<UUID, Long> countsByCatalog = new HashMap<>();
        for (Object[] row : rows) {
            countsByCatalog.put((UUID) row[0], (Long) row[1]);
        }
        return countsByCatalog;
    }

    public List<String> listCategories(UUID catalogId) {
        return em.createQuery(
                        "SELECT DISTINCT d.category FROM SkillDefinition d WHERE d.catalogId = :catalogId ORDER BY d.category",
                        String.class)
                .setParameter("catalogId", catalogId)
                .getResultList();
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

    public List<Object[]> findSimilarByEmbedding(float[] embedding, int limit, double minSimilarity) {
        if (embedding == null || embedding.length == 0 || limit <= 0) {
            return Collections.emptyList();
        }
        String vectorLiteral = VectorSqlUtils.vectorToSqlLiteral(embedding);
        String sql = """
                SELECT d.id, d.name, d.aliases, (1 - (d.embedding <=> cast(:vec as vector))) as cosine_sim
                FROM skills.skill_definition d
                WHERE d.embedding IS NOT NULL
                  AND (1 - (d.embedding <=> cast(:vec as vector))) >= :minSimilarity
                ORDER BY d.embedding <=> cast(:vec as vector)
                LIMIT :limit
                """;
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(sql)
                .setParameter("vec", vectorLiteral)
                .setParameter("minSimilarity", minSimilarity)
                .setParameter("limit", limit)
                .getResultList();
        return rows;
    }
}
