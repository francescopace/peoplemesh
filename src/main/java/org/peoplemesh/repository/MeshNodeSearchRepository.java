package org.peoplemesh.repository;

import io.micrometer.core.annotation.Timed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.util.SqlParsingUtils;
import org.peoplemesh.util.VectorSqlUtils;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class MeshNodeSearchRepository {

    public record UserCandidateRow(
            UUID nodeId,
            UUID userId,
            String seniority,
            List<String> skillsTechnical,
            List<String> skillsSoft,
            List<String> toolsAndTech,
            String workMode,
            String employmentType,
            List<String> learningAreas,
            String country,
            String timezone,
            Instant updatedAt,
            String city,
            double cosineSim,
            String displayName,
            String roles,
            List<String> hobbies,
            List<String> sports,
            List<String> causes,
            String avatarUrl,
            String slackHandle,
            String email,
            String telegramHandle,
            String mobilePhone,
            String linkedinUrl
    ) {}

    public record NodeCandidateRow(
            UUID nodeId,
            String nodeType,
            String title,
            String description,
            List<String> tags,
            String country,
            Instant updatedAt,
            double cosineSim
    ) {}

    public record UnifiedSearchRow(
            UUID nodeId,
            String nodeType,
            String title,
            String description,
            List<String> tags,
            String country,
            Instant updatedAt,
            String structuredDataJson,
            double cosineSim
    ) {}

    private static final String USER_MATCH_CANDIDATE_SQL =
            "SELECT n.id AS node_id, n.id AS user_id, n.structured_data->>'seniority' AS seniority, n.tags AS skills_technical, "
                    + "(SELECT COALESCE(array_agg(x), ARRAY[]::text[]) FROM jsonb_array_elements_text(COALESCE(n.structured_data->'skills_soft', '[]'::jsonb)) AS t(x)) AS skills_soft, "
                    + "(SELECT COALESCE(array_agg(x), ARRAY[]::text[]) FROM jsonb_array_elements_text(COALESCE(n.structured_data->'tools_and_tech', '[]'::jsonb)) AS t(x)) AS tools_and_tech, "
                    + "n.structured_data->>'work_mode' AS work_mode, n.structured_data->>'employment_type' AS employment_type, "
                    + "(SELECT COALESCE(array_agg(x), ARRAY[]::text[]) FROM jsonb_array_elements_text(COALESCE(n.structured_data->'learning_areas', '[]'::jsonb)) AS t(x)) AS learning_areas, "
                    + "n.country AS country, n.structured_data->>'timezone' AS timezone, n.updated_at AS updated_at, n.structured_data->>'city' AS city, "
                    + "(1 - (n.embedding <=> cast(:vec as vector))) AS cosine_sim, "
                    + "n.title AS display_name, n.description AS roles, "
                    + "(SELECT COALESCE(array_agg(x), ARRAY[]::text[]) FROM jsonb_array_elements_text(COALESCE(n.structured_data->'hobbies', '[]'::jsonb)) AS t(x)) AS hobbies, "
                    + "(SELECT COALESCE(array_agg(x), ARRAY[]::text[]) FROM jsonb_array_elements_text(COALESCE(n.structured_data->'sports', '[]'::jsonb)) AS t(x)) AS sports, "
                    + "(SELECT COALESCE(array_agg(x), ARRAY[]::text[]) FROM jsonb_array_elements_text(COALESCE(n.structured_data->'causes', '[]'::jsonb)) AS t(x)) AS causes, "
                    + "n.structured_data->>'avatar_url' AS avatar_url, "
                    + "n.structured_data->>'slack_handle' AS slack_handle, n.structured_data->>'email' AS email, "
                    + "n.structured_data->>'telegram_handle' AS telegram_handle, n.structured_data->>'mobile_phone' AS mobile_phone, "
                    + "n.structured_data->>'linkedin_url' AS linkedin_url "
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
    public List<UserCandidateRow> findUserCandidatesByEmbedding(float[] embedding, UUID excludeUserId, int poolSize) {
        String vectorLiteral = VectorSqlUtils.vectorToSqlLiteral(embedding);
        List<Object[]> rows = em.createNativeQuery(
                        USER_MATCH_CANDIDATE_SQL
                                + "AND n.id != :excludeUserId "
                                + "ORDER BY n.embedding <=> cast(:vec as vector) "
                                + "LIMIT :poolSize")
                .setParameter("vec", vectorLiteral)
                .setParameter("excludeUserId", excludeUserId)
                .setParameter("poolSize", poolSize)
                .getResultList();
        return mapUserCandidates(rows);
    }

    private List<UserCandidateRow> mapUserCandidates(List<Object[]> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<UserCandidateRow> mapped = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            mapped.add(new UserCandidateRow(
                    (UUID) row[0],
                    (UUID) row[1],
                    (String) row[2],
                    SqlParsingUtils.parseArray(row[3]),
                    SqlParsingUtils.parseArray(row[4]),
                    SqlParsingUtils.parseArray(row[5]),
                    (String) row[6],
                    (String) row[7],
                    SqlParsingUtils.parseArray(row[8]),
                    (String) row[9],
                    (String) row[10],
                    SqlParsingUtils.toInstant(row[11]),
                    (String) row[12],
                    ((Number) row[13]).doubleValue(),
                    (String) row[14],
                    (String) row[15],
                    SqlParsingUtils.parseArray(row[16]),
                    SqlParsingUtils.parseArray(row[17]),
                    SqlParsingUtils.parseArray(row[18]),
                    (String) row[19],
                    (String) row[20],
                    (String) row[21],
                    (String) row[22],
                    (String) row[23],
                    (String) row[24]
            ));
        }
        return mapped;
    }

    @SuppressWarnings("unchecked")
    @Timed(
            value = "peoplemesh.hnsw.search.node",
            description = "HNSW vector search latency",
            percentiles = {0.95},
            histogram = true
    )
    public List<NodeCandidateRow> findNodeCandidatesByEmbedding(float[] embedding, UUID excludeUserId,
                                                                NodeType targetType, int poolSize) {
        String vectorLiteral = VectorSqlUtils.vectorToSqlLiteral(embedding);
        String typeSql = targetType != null ? "AND n.node_type = :nodeType " : "AND n.node_type != 'USER' ";
        String sql = "SELECT n.id AS node_id, n.node_type AS node_type, n.title AS title, n.description AS description, n.tags AS tags, "
                + "n.country AS country, n.updated_at AS updated_at, "
                + "(1 - (n.embedding <=> cast(:vec as vector))) AS cosine_sim "
                + "FROM mesh.mesh_node n "
                + "WHERE n.embedding IS NOT NULL "
                + "AND n.id != :userId "
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
        List<Object[]> rows = query.getResultList();
        return mapNodeCandidates(rows);
    }

    private List<NodeCandidateRow> mapNodeCandidates(List<Object[]> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<NodeCandidateRow> mapped = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            mapped.add(new NodeCandidateRow(
                    (UUID) row[0],
                    (String) row[1],
                    (String) row[2],
                    (String) row[3],
                    SqlParsingUtils.parseArray(row[4]),
                    (String) row[5],
                    SqlParsingUtils.toInstant(row[6]),
                    ((Number) row[7]).doubleValue()
            ));
        }
        return mapped;
    }

    @SuppressWarnings("unchecked")
    @Timed(
            value = "peoplemesh.hnsw.search.unified",
            description = "HNSW vector search latency",
            percentiles = {0.95},
            histogram = true
    )
    public List<UnifiedSearchRow> unifiedVectorSearch(float[] embedding, UUID excludeUserId,
                                                      String countryFilter, List<String> languages,
                                                      NodeType targetType, int poolSize) {
        String vectorLiteral = VectorSqlUtils.vectorToSqlLiteral(embedding);

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT n.id AS node_id, n.node_type AS node_type, n.title AS title, n.description AS description, n.tags AS tags, n.country AS country, ");
        sql.append("n.updated_at AS updated_at, CASE WHEN n.node_type = 'USER' THEN n.structured_data ELSE NULL END AS structured_data, ");
        sql.append("(1 - (n.embedding <=> cast(:vec as vector))) AS cosine_sim ");
        sql.append("FROM mesh.mesh_node n ");
        sql.append("WHERE n.embedding IS NOT NULL ");
        sql.append("AND n.id != :userId ");

        Map<String, Object> params = new HashMap<>();
        params.put("vec", vectorLiteral);
        params.put("userId", excludeUserId);

        if (countryFilter != null && !countryFilter.isBlank()) {
            sql.append("AND UPPER(n.country) = :countryFilter ");
            params.put("countryFilter", countryFilter.toUpperCase());
        }

        if (targetType != null) {
            sql.append("AND n.node_type = :nodeType ");
            params.put("nodeType", targetType.name());
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
        List<Object[]> rows = query.getResultList();
        return mapUnifiedSearchRows(rows);
    }

    private List<UnifiedSearchRow> mapUnifiedSearchRows(List<Object[]> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<UnifiedSearchRow> mapped = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            mapped.add(new UnifiedSearchRow(
                    (UUID) row[0],
                    (String) row[1],
                    (String) row[2],
                    (String) row[3],
                    SqlParsingUtils.parseArray(row[4]),
                    (String) row[5],
                    SqlParsingUtils.toInstant(row[6]),
                    row[7] != null ? row[7].toString() : null,
                    ((Number) row[8]).doubleValue()
            ));
        }
        return mapped;
    }
}
