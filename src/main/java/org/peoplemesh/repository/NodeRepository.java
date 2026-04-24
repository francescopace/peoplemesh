package org.peoplemesh.repository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.model.MeshNode;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class NodeRepository {

    private static final int DEFAULT_EMBEDDING_JOB_LIMIT = 20_000;

    @Inject
    EntityManager em;

    public Optional<MeshNode> findById(UUID nodeId) {
        return em.createQuery("FROM MeshNode n WHERE n.id = :nodeId", MeshNode.class)
                .setParameter("nodeId", nodeId)
                .getResultStream()
                .findFirst();
    }

    public List<MeshNode> listNodes(NodeType nodeType, Boolean searchable, int page, int size) {
        StringBuilder q = new StringBuilder("FROM MeshNode n WHERE 1=1 ");
        if (nodeType != null) {
            q.append("AND n.nodeType = :nodeType ");
        }
        if (searchable != null) {
            q.append("AND n.searchable = :searchable ");
        }
        q.append("ORDER BY n.updatedAt DESC");
        var query = em.createQuery(q.toString(), MeshNode.class)
                .setFirstResult(Math.max(0, page) * Math.max(1, size))
                .setMaxResults(Math.max(1, size));
        if (nodeType != null) {
            query.setParameter("nodeType", nodeType);
        }
        if (searchable != null) {
            query.setParameter("searchable", searchable);
        }
        return query.getResultList();
    }

    public Optional<MeshNode> findPublishedUserNode(UUID nodeId) {
        return em.createQuery("FROM MeshNode n WHERE n.id = :nodeId AND n.nodeType = :nodeType", MeshNode.class)
                .setParameter("nodeId", nodeId)
                .setParameter("nodeType", NodeType.USER)
                .getResultStream()
                .findFirst();
    }

    public Optional<MeshNode> findUserByExternalId(String externalId) {
        if (externalId == null || externalId.isBlank()) {
            return Optional.empty();
        }
        return em.createQuery(
                        "FROM MeshNode n WHERE n.externalId = :externalId AND n.nodeType = :nodeType",
                        MeshNode.class)
                .setParameter("externalId", externalId)
                .setParameter("nodeType", NodeType.USER)
                .getResultStream()
                .findFirst();
    }

    public Optional<MeshNode> findJobBySourceAndExternalId(String source, String externalId) {
        return findByTypeAndSourceAndExternalId(NodeType.JOB, source, externalId);
    }

    public Optional<MeshNode> findByTypeAndSourceAndExternalId(NodeType nodeType, String source, String externalId) {
        return em.createQuery(
                        "FROM MeshNode n WHERE n.nodeType = :nodeType " +
                                "AND n.structuredData IS NOT NULL " +
                                "AND CAST(function('jsonb_extract_path_text', n.structuredData, 'source') AS string) = :source " +
                                "AND CAST(function('jsonb_extract_path_text', n.structuredData, 'external_id') AS string) = :externalId",
                        MeshNode.class)
                .setParameter("nodeType", nodeType)
                .setParameter("source", source)
                .setParameter("externalId", externalId)
                .getResultStream()
                .findFirst();
    }

    public List<UUID> findNodeIds(NodeType nodeType, boolean onlyMissing) {
        StringBuilder q = new StringBuilder("select n.id from MeshNode n where 1=1 ");
        if (nodeType != null) {
            q.append("and n.nodeType = :nodeType ");
        }
        if (onlyMissing) {
            q.append("and n.embedding is null ");
        }
        var query = em.createQuery(q.toString(), UUID.class)
                .setMaxResults(DEFAULT_EMBEDDING_JOB_LIMIT);
        if (nodeType != null) {
            query.setParameter("nodeType", nodeType);
        }
        return query.getResultList();
    }

    public List<MeshNode> findByIds(List<UUID> nodeIds) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            return List.of();
        }
        return em.createQuery("select n from MeshNode n where n.id in :ids", MeshNode.class)
                .setParameter("ids", nodeIds)
                .getResultList();
    }

    public List<UUID> findUserIdsWithMissingEmbeddingByIdentityProvider(String provider) {
        @SuppressWarnings("unchecked")
        List<Object> rows = em.createNativeQuery("""
                SELECT mn.id FROM mesh.mesh_node mn
                JOIN identity.user_identity ui ON ui.node_id = mn.id
                WHERE ui.oauth_provider = :provider
                  AND mn.node_type = 'USER'
                  AND mn.embedding IS NULL
                """)
                .setParameter("provider", provider)
                .setMaxResults(DEFAULT_EMBEDDING_JOB_LIMIT)
                .getResultList();
        return rows.stream().map(r -> (UUID) (r instanceof Object[] arr ? arr[0] : r)).toList();
    }

    public List<MeshNode> listCommunities() {
        return em.createQuery("FROM MeshNode n WHERE n.nodeType = :nodeType", MeshNode.class)
                .setParameter("nodeType", NodeType.COMMUNITY)
                .getResultList();
    }

    public long countByType(NodeType type) {
        return em.createQuery("SELECT COUNT(n) FROM MeshNode n WHERE n.nodeType = :type", Long.class)
                .setParameter("type", type)
                .getSingleResult();
    }

    public long countByTypes(List<NodeType> types) {
        return em.createQuery("SELECT COUNT(n) FROM MeshNode n WHERE n.nodeType in :types", Long.class)
                .setParameter("types", types)
                .getSingleResult();
    }

    public long countAll() {
        return em.createQuery("SELECT COUNT(n) FROM MeshNode n", Long.class)
                .getSingleResult();
    }

    public long countSearchableNodes() {
        return em.createQuery("SELECT COUNT(n) FROM MeshNode n WHERE n.searchable = true", Long.class)
                .getSingleResult();
    }

    public long countSearchableNodesWithEmbedding() {
        return em.createQuery(
                        "SELECT COUNT(n) FROM MeshNode n WHERE n.searchable = true AND n.embedding IS NOT NULL",
                        Long.class
                )
                .getSingleResult();
    }

    public void persist(MeshNode node) {
        RepositoryPersistence.persistOrMerge(em, node, node.id);
    }

    public void delete(MeshNode node) {
        MeshNode attached = em.contains(node) ? node : em.merge(node);
        em.remove(attached);
    }

    public void flush() {
        em.flush();
    }
}
