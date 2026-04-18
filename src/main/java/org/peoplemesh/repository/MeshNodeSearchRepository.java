package org.peoplemesh.repository;

import io.micrometer.core.annotation.Timed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.util.VectorSqlUtils;

import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class MeshNodeSearchRepository {

    private static final String USER_MATCH_CANDIDATE_SQL =
            "SELECT n.id, n.created_by, n.structured_data->>'seniority', n.tags, "
                    + "(SELECT COALESCE(array_agg(x), ARRAY[]::text[]) FROM jsonb_array_elements_text(COALESCE(n.structured_data->'skills_soft', '[]'::jsonb)) AS t(x)), "
                    + "(SELECT COALESCE(array_agg(x), ARRAY[]::text[]) FROM jsonb_array_elements_text(COALESCE(n.structured_data->'tools_and_tech', '[]'::jsonb)) AS t(x)), "
                    + "n.structured_data->>'work_mode', n.structured_data->>'employment_type', "
                    + "(SELECT COALESCE(array_agg(x), ARRAY[]::text[]) FROM jsonb_array_elements_text(COALESCE(n.structured_data->'topics_frequent', '[]'::jsonb)) AS t(x)), "
                    + "(SELECT COALESCE(array_agg(x), ARRAY[]::text[]) FROM jsonb_array_elements_text(COALESCE(n.structured_data->'learning_areas', '[]'::jsonb)) AS t(x)), "
                    + "n.country, n.structured_data->>'timezone', n.updated_at, n.structured_data->>'city', "
                    + "(1 - (n.embedding <=> cast(:vec as vector))) as cosine_sim, "
                    + "n.title, n.description, "
                    + "(SELECT COALESCE(array_agg(x), ARRAY[]::text[]) FROM jsonb_array_elements_text(COALESCE(n.structured_data->'hobbies', '[]'::jsonb)) AS t(x)), "
                    + "(SELECT COALESCE(array_agg(x), ARRAY[]::text[]) FROM jsonb_array_elements_text(COALESCE(n.structured_data->'sports', '[]'::jsonb)) AS t(x)), "
                    + "(SELECT COALESCE(array_agg(x), ARRAY[]::text[]) FROM jsonb_array_elements_text(COALESCE(n.structured_data->'causes', '[]'::jsonb)) AS t(x)), "
                    + "n.structured_data->>'avatar_url', "
                    + "n.structured_data->>'slack_handle', n.structured_data->>'email', "
                    + "n.structured_data->>'telegram_handle', n.structured_data->>'mobile_phone' "
                    + "FROM mesh.mesh_node n "
                    + "WHERE n.node_type = 'USER' "
                    + "AND n.searchable = true AND n.embedding IS NOT NULL ";

    @Inject
    EntityManager em;

    @SuppressWarnings("unchecked")
    @Timed(
            value = "peoplemesh.hnsw.search.user",
            description = "HNSW vector search latency",
            percentiles = {0.95},
            histogram = true
    )
    public List<Object[]> findUserCandidatesByEmbedding(float[] embedding, UUID excludeUserId, int poolSize) {
        String vectorLiteral = VectorSqlUtils.vectorToSqlLiteral(embedding);
        return em.createNativeQuery(
                        USER_MATCH_CANDIDATE_SQL
                                + "AND n.created_by != :excludeUserId "
                                + "ORDER BY n.embedding <=> cast(:vec as vector) "
                                + "LIMIT :poolSize")
                .setParameter("vec", vectorLiteral)
                .setParameter("excludeUserId", excludeUserId)
                .setParameter("poolSize", poolSize)
                .getResultList();
    }

    @SuppressWarnings("unchecked")
    @Timed(
            value = "peoplemesh.hnsw.search.node",
            description = "HNSW vector search latency",
            percentiles = {0.95},
            histogram = true
    )
    public List<Object[]> findNodeCandidatesByEmbedding(float[] embedding, UUID excludeUserId,
                                                         NodeType targetType, int poolSize) {
        String vectorLiteral = VectorSqlUtils.vectorToSqlLiteral(embedding);
        String typeSql = targetType != null ? "AND n.node_type = :nodeType " : "AND n.node_type != 'USER' ";
        String sql = "SELECT n.id, n.node_type, n.title, n.description, n.tags, "
                + "n.country, n.updated_at, "
                + "(1 - (n.embedding <=> cast(:vec as vector))) as cosine_sim "
                + "FROM mesh.mesh_node n "
                + "WHERE n.embedding IS NOT NULL "
                + "AND n.created_by != :userId "
                + typeSql
                + "ORDER BY n.embedding <=> cast(:vec as vector) "
                + "LIMIT :poolSize";

        var query = em.createNativeQuery(sql)
                .setParameter("vec", vectorLiteral)
                .setParameter("userId", excludeUserId)
                .setParameter("poolSize", poolSize);
        if (targetType != null) {
            query.setParameter("nodeType", targetType.name());
        }
        return query.getResultList();
    }

    @SuppressWarnings("unchecked")
    @Timed(
            value = "peoplemesh.hnsw.search.unified",
            description = "HNSW vector search latency",
            percentiles = {0.95},
            histogram = true
    )
    public List<Object[]> unifiedVectorSearch(float[] embedding, UUID excludeUserId,
                                               String countryFilter, List<String> languages,
                                               int poolSize) {
        String vectorLiteral = VectorSqlUtils.vectorToSqlLiteral(embedding);

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT n.id, n.node_type, n.title, n.description, n.tags, n.country, ");
        sql.append("n.updated_at, CASE WHEN n.node_type = 'USER' THEN n.structured_data ELSE NULL END as structured_data, ");
        sql.append("(1 - (n.embedding <=> cast(:vec as vector))) as cosine_sim ");
        sql.append("FROM mesh.mesh_node n ");
        sql.append("WHERE n.embedding IS NOT NULL ");
        sql.append("AND n.created_by != :userId ");

        Map<String, Object> params = new HashMap<>();
        params.put("vec", vectorLiteral);
        params.put("userId", excludeUserId);

        if (countryFilter != null && !countryFilter.isBlank()) {
            sql.append("AND UPPER(n.country) = :countryFilter ");
            params.put("countryFilter", countryFilter.toUpperCase());
        }

        if (languages != null && !languages.isEmpty()) {
            sql.append("AND (n.node_type != 'USER' OR EXISTS (");
            sql.append("SELECT 1 FROM jsonb_array_elements_text(COALESCE(n.structured_data->'languages_spoken', '[]'::jsonb)) AS lang(value) ");
            sql.append("WHERE lower(lang.value) = ANY(cast(:langs as text[]))");
            sql.append(")) ");
            String escapedLangs = languages.stream()
                    .map(l -> l.toLowerCase(Locale.ROOT))
                    .map(l -> "\"" + l.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
                    .collect(Collectors.joining(",", "{", "}"));
            params.put("langs", escapedLangs);
        }

        sql.append("ORDER BY n.embedding <=> cast(:vec as vector) ");
        sql.append("LIMIT :poolSize");
        params.put("poolSize", poolSize);

        var query = em.createNativeQuery(sql.toString());
        params.forEach(query::setParameter);
        return query.getResultList();
    }
}
