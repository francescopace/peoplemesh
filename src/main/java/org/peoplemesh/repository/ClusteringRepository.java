package org.peoplemesh.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class ClusteringRepository {

    private static final int MAX_EMBEDDINGS_PER_RUN = 20_000;

    @Inject
    EntityManager em;

    @SuppressWarnings("unchecked")
    public List<Object[]> loadPublishedEmbeddings() {
        return em.createNativeQuery(
                        "SELECT id, embedding::text FROM mesh.mesh_node " +
                                "WHERE node_type = 'USER' AND searchable = true AND embedding IS NOT NULL " +
                                "ORDER BY updated_at DESC")
                .setMaxResults(MAX_EMBEDDINGS_PER_RUN)
                .getResultList();
    }

    @SuppressWarnings("unchecked")
    public List<Object[]> loadClusterTraits(List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return em.createNativeQuery(
                        "SELECT tags, " +
                                "structured_data->'hobbies', structured_data->'sports', " +
                                "structured_data->'causes', structured_data->'topics_frequent', country " +
                                "FROM mesh.mesh_node WHERE node_type = 'USER' AND id IN :ids")
                .setParameter("ids", userIds)
                .getResultList();
    }
}
