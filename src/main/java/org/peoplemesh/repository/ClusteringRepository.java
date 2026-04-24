package org.peoplemesh.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class ClusteringRepository {

    public record PublishedEmbeddingRow(UUID userId, String embeddingText) {}

    public record ClusterTraitsRow(
            Object tags,
            Object hobbies,
            Object sports,
            Object causes,
            String country
    ) {}

    private static final int MAX_EMBEDDINGS_PER_RUN = 20_000;

    @Inject
    EntityManager em;

    @SuppressWarnings("unchecked")
    public List<PublishedEmbeddingRow> loadPublishedEmbeddings() {
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT id AS user_id, embedding::text AS embedding_text FROM mesh.mesh_node " +
                                "WHERE node_type = 'USER' AND searchable = true AND embedding IS NOT NULL " +
                                "ORDER BY updated_at DESC")
                .setMaxResults(MAX_EMBEDDINGS_PER_RUN)
                .getResultList();
        List<PublishedEmbeddingRow> mapped = new java.util.ArrayList<>(rows.size());
        for (Object[] row : rows) {
            mapped.add(new PublishedEmbeddingRow((UUID) row[0], (String) row[1]));
        }
        return mapped;
    }

    @SuppressWarnings("unchecked")
    public List<ClusterTraitsRow> loadClusterTraits(List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT tags AS skills, " +
                                "structured_data->'hobbies' AS hobbies, structured_data->'sports' AS sports, " +
                                "structured_data->'causes' AS causes, country AS country " +
                                "FROM mesh.mesh_node WHERE node_type = 'USER' AND id IN :ids")
                .setParameter("ids", userIds)
                .getResultList();
        List<ClusterTraitsRow> mapped = new java.util.ArrayList<>(rows.size());
        for (Object[] row : rows) {
            mapped.add(new ClusterTraitsRow(row[0], row[1], row[2], row[3], row[4] != null ? row[4].toString() : null));
        }
        return mapped;
    }
}
