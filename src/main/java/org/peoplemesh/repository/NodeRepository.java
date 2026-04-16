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

    private static final int DEFAULT_OWNER_LIST_LIMIT = 200;
    private static final int DEFAULT_EMBEDDING_JOB_LIMIT = 20_000;

    @Inject
    EntityManager em;

    public Optional<MeshNode> findById(UUID nodeId) {
        return em.createQuery("FROM MeshNode n WHERE n.id = :nodeId", MeshNode.class)
                .setParameter("nodeId", nodeId)
                .getResultStream()
                .findFirst();
    }

    public Optional<MeshNode> findByIdAndOwner(UUID nodeId, UUID ownerUserId) {
        return em.createQuery("FROM MeshNode n WHERE n.id = :nodeId AND n.createdBy = :ownerUserId", MeshNode.class)
                .setParameter("nodeId", nodeId)
                .setParameter("ownerUserId", ownerUserId)
                .getResultStream()
                .findFirst();
    }

    public List<MeshNode> findByOwner(UUID ownerUserId) {
        return findByOwner(ownerUserId, DEFAULT_OWNER_LIST_LIMIT);
    }

    public List<MeshNode> findByOwner(UUID ownerUserId, int limit) {
        return em.createQuery("FROM MeshNode n WHERE n.createdBy = :ownerUserId ORDER BY n.updatedAt DESC", MeshNode.class)
                .setParameter("ownerUserId", ownerUserId)
                .setMaxResults(limit)
                .getResultList();
    }

    public List<MeshNode> findByOwnerAndType(UUID ownerUserId, NodeType type) {
        return em.createQuery(
                        "FROM MeshNode n WHERE n.createdBy = :ownerUserId AND n.nodeType = :type ORDER BY n.updatedAt DESC",
                        MeshNode.class)
                .setParameter("ownerUserId", ownerUserId)
                .setParameter("type", type)
                .setMaxResults(DEFAULT_OWNER_LIST_LIMIT)
                .getResultList();
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

    public Optional<MeshNode> findJobByExternalId(UUID ownerUserId, String externalId) {
        return em.createQuery(
                        "FROM MeshNode n WHERE n.createdBy = :ownerUserId AND n.nodeType = :nodeType " +
                                "AND n.structuredData IS NOT NULL " +
                                "AND CAST(function('jsonb_extract_path_text', n.structuredData, 'external_id') AS string) = :externalId",
                        MeshNode.class)
                .setParameter("ownerUserId", ownerUserId)
                .setParameter("nodeType", NodeType.JOB)
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

    public void persist(MeshNode node) {
        if (node.id == null) {
            em.persist(node);
        } else {
            em.merge(node);
        }
    }

    public void delete(MeshNode node) {
        MeshNode attached = em.contains(node) ? node : em.merge(node);
        em.remove(attached);
    }

    public void flush() {
        em.flush();
    }
}
