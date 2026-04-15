package org.peoplemesh.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.model.MeshNode;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class NodeEmbeddingMaintenanceService {

    private static final Logger LOG = Logger.getLogger(NodeEmbeddingMaintenanceService.class);

    @Inject
    EmbeddingService embeddingService;

    @Inject
    AuditService auditService;

    public EmbeddingRegenerationResult regenerateEmbeddings(UUID actorId, NodeType nodeType, boolean onlyMissing) {
        List<UUID> nodeIds = findNodeIds(nodeType, onlyMissing);
        int succeeded = 0;
        int failed = 0;

        for (UUID nodeId : nodeIds) {
            try {
                regenerateSingleNodeEmbedding(nodeId);
                succeeded++;
            } catch (Exception e) {
                failed++;
                LOG.warnf("Embedding regeneration failed for nodeId=%s: %s", nodeId, e.getMessage());
            }
        }

        String metadata = "{\"processed\":%d,\"succeeded\":%d,\"failed\":%d,\"nodeType\":\"%s\",\"onlyMissing\":%s}"
                .formatted(nodeIds.size(), succeeded, failed, nodeType == null ? "ALL" : nodeType.name(), onlyMissing);
        auditService.log(actorId, "MAINTENANCE_REGENERATE_EMBEDDINGS", "maintenance_regenerate_embeddings", null, metadata);

        LOG.infof("Maintenance embedding regeneration completed: processed=%d succeeded=%d failed=%d nodeType=%s onlyMissing=%s",
                nodeIds.size(), succeeded, failed, nodeType == null ? "ALL" : nodeType.name(), onlyMissing);
        return new EmbeddingRegenerationResult(nodeIds.size(), succeeded, failed, nodeType, onlyMissing);
    }

    List<UUID> findNodeIds(NodeType nodeType, boolean onlyMissing) {
        if (nodeType == null) {
            if (onlyMissing) {
                return MeshNode.getEntityManager()
                        .createQuery("select n.id from MeshNode n where n.embedding is null", UUID.class)
                        .getResultList();
            }
            return MeshNode.getEntityManager()
                    .createQuery("select n.id from MeshNode n", UUID.class)
                    .getResultList();
        }
        if (onlyMissing) {
            return MeshNode.getEntityManager()
                    .createQuery("select n.id from MeshNode n where n.nodeType = :nodeType and n.embedding is null", UUID.class)
                    .setParameter("nodeType", nodeType)
                    .getResultList();
        }
        return MeshNode.getEntityManager()
                .createQuery("select n.id from MeshNode n where n.nodeType = :nodeType", UUID.class)
                .setParameter("nodeType", nodeType)
                .getResultList();
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    void regenerateSingleNodeEmbedding(UUID nodeId) {
        MeshNode node = MeshNode.<MeshNode>findByIdOptional(nodeId).orElse(null);
        if (node == null) {
            return;
        }
        String text = EmbeddingTextBuilder.buildText(node);
        float[] embedding = embeddingService.generateEmbedding(text);
        node.embedding = embedding;
        node.searchable = embedding != null;
        node.persist();
    }

    public record EmbeddingRegenerationResult(
            int processed,
            int succeeded,
            int failed,
            NodeType nodeType,
            boolean onlyMissing
    ) {
    }
}
