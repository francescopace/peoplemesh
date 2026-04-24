package org.peoplemesh.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.peoplemesh.domain.model.SkillDefinition;
import org.peoplemesh.util.SkillNameNormalizer;
import org.peoplemesh.util.SqlParsingUtils;
import org.peoplemesh.util.VectorSqlUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class SkillDefinitionRepository {

    public record SimilarSkillRow(
            UUID skillId,
            String skillName,
            List<String> aliases,
            double cosineSimilarity
    ) {}

    @Inject
    EntityManager em;

    public List<SkillDefinition> findAll() {
        return em.createQuery("FROM SkillDefinition d ORDER BY d.name", SkillDefinition.class)
                .getResultList();
    }

    public long countAll() {
        return em.createQuery("SELECT COUNT(d) FROM SkillDefinition d", Long.class)
                .getSingleResult();
    }

    public Optional<SkillDefinition> findByName(String name) {
        String normalized = SkillNameNormalizer.normalize(name);
        if (normalized == null) {
            return Optional.empty();
        }
        return em.createQuery(
                        "FROM SkillDefinition d WHERE d.name = :name",
                        SkillDefinition.class)
                .setParameter("name", normalized)
                .getResultStream()
                .findFirst();
    }

    public Optional<SkillDefinition> findByNameOrAlias(String term) {
        String normalized = SkillNameNormalizer.normalize(term);
        if (normalized == null) {
            return Optional.empty();
        }
        String sql = """
                SELECT d.*
                FROM skills.skill_definition d
                WHERE d.name = :normalized
                   OR EXISTS (
                        SELECT 1 FROM unnest(d.aliases) a
                        WHERE LOWER(a) = :normalized
                   )
                ORDER BY d.usage_count DESC, d.name ASC
                LIMIT 1
                """;
        @SuppressWarnings("unchecked")
        List<SkillDefinition> rows = em.createNativeQuery(sql, SkillDefinition.class)
                .setParameter("normalized", normalized)
                .getResultList();
        return rows.stream().findFirst();
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

    public List<SkillDefinition> findByNames(List<String> names) {
        if (names == null || names.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> cleaned = names.stream()
                .map(SkillNameNormalizer::normalize)
                .filter(n -> n != null && !n.isBlank())
                .distinct()
                .toList();
        if (cleaned.isEmpty()) {
            return Collections.emptyList();
        }
        return em.createQuery("FROM SkillDefinition d WHERE d.name IN :names", SkillDefinition.class)
                .setParameter("names", cleaned)
                .getResultList();
    }

    public void upsert(SkillDefinition skillDefinition) {
        RepositoryPersistence.persistOrMerge(em, skillDefinition, skillDefinition.id);
    }

    public List<SkillDefinition> listSkills(int page, int size) {
        return em.createQuery("FROM SkillDefinition d ORDER BY d.usageCount DESC, d.name ASC", SkillDefinition.class)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();
    }

    public List<SkillDefinition> suggestByAlias(String term, int limit) {
        if (term == null || term.isBlank() || limit <= 0) {
            return Collections.emptyList();
        }
        String normalized = SkillNameNormalizer.normalize(term);
        if (normalized == null) {
            return Collections.emptyList();
        }
        String pattern = "%" + normalized + "%";
        String sql = """
                SELECT d.*
                FROM skills.skill_definition d
                WHERE d.name LIKE :pattern
                   OR EXISTS (
                        SELECT 1 FROM unnest(d.aliases) a
                        WHERE LOWER(a) LIKE :pattern
                   )
                ORDER BY d.usage_count DESC, d.name ASC
                LIMIT :limit
                """;
        @SuppressWarnings("unchecked")
        List<SkillDefinition> rows = em.createNativeQuery(sql, SkillDefinition.class)
                .setParameter("pattern", pattern)
                .setParameter("limit", limit)
                .getResultList();
        return rows;
    }

    public int incrementUsageCounts(List<String> canonicalNames) {
        List<String> cleaned = sanitizeNames(canonicalNames);
        if (cleaned.isEmpty()) {
            return 0;
        }
        return em.createQuery(
                        "UPDATE SkillDefinition d " +
                                "SET d.usageCount = d.usageCount + 1, d.updatedAt = CURRENT_TIMESTAMP " +
                                "WHERE d.name IN :names")
                .setParameter("names", cleaned)
                .executeUpdate();
    }

    public int decrementUsageCounts(List<String> canonicalNames) {
        List<String> cleaned = sanitizeNames(canonicalNames);
        if (cleaned.isEmpty()) {
            return 0;
        }
        return em.createQuery(
                        "UPDATE SkillDefinition d " +
                                "SET d.usageCount = CASE WHEN d.usageCount > 0 THEN d.usageCount - 1 ELSE 0 END, " +
                                "d.updatedAt = CURRENT_TIMESTAMP " +
                                "WHERE d.name IN :names")
                .setParameter("names", cleaned)
                .executeUpdate();
    }

    public int deleteUnused() {
        return em.createQuery("DELETE FROM SkillDefinition d WHERE d.usageCount = 0")
                .executeUpdate();
    }

    public List<SimilarSkillRow> findSimilarByEmbedding(float[] embedding, int limit, double minSimilarity) {
        if (embedding == null || embedding.length == 0 || limit <= 0) {
            return Collections.emptyList();
        }
        String vectorLiteral = VectorSqlUtils.vectorToSqlLiteral(embedding);
        String sql = """
                SELECT d.id AS skill_id, d.name AS skill_name, d.aliases AS aliases,
                       (1 - (d.embedding <=> cast(:vec as vector))) AS cosine_sim
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
        List<SimilarSkillRow> mapped = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            mapped.add(new SimilarSkillRow(
                    (UUID) row[0],
                    row[1] != null ? row[1].toString() : null,
                    SqlParsingUtils.parseArray(row[2]),
                    row[3] instanceof Number n ? n.doubleValue() : 0.0
            ));
        }
        return mapped;
    }

    private static List<String> sanitizeNames(List<String> canonicalNames) {
        if (canonicalNames == null || canonicalNames.isEmpty()) {
            return List.of();
        }
        return canonicalNames.stream()
                .map(SkillNameNormalizer::normalize)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .toList();
    }
}
