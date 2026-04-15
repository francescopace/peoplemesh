package org.peoplemesh.service;

import org.peoplemesh.domain.dto.NodeDto;
import org.peoplemesh.domain.dto.NodePayload;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.model.MeshNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class NodeService {

    private static final Logger LOG = Logger.getLogger(NodeService.class);

    @Inject
    EmbeddingService embeddingService;

    @Inject
    AuditService auditService;

    @Transactional
    public NodeDto createNode(UUID ownerUserId, NodePayload payload) {
        MeshNode node = createEmptyNode();
        node.createdBy = ownerUserId;
        applyPayload(node, payload);
        node.embedding = generateEmbedding(nodeToText(node));
        persistNode(node);
        LOG.infof("action=createNode userId=%s nodeId=%s type=%s", ownerUserId, node.id, node.nodeType);
        logAudit(ownerUserId, "NODE_CREATED", "mesh_create_node");
        return toDto(node);
    }

    @Transactional
    public Optional<NodeDto> updateNode(UUID ownerUserId, UUID nodeId, NodePayload payload) {
        return findNodeByIdAndOwner(nodeId, ownerUserId)
                .map(node -> {
                    applyPayload(node, payload);
                    node.embedding = generateEmbedding(nodeToText(node));
                    persistNode(node);
                    LOG.infof("action=updateNode userId=%s nodeId=%s", ownerUserId, nodeId);
                    logAudit(ownerUserId, "NODE_UPDATED", "mesh_update_node");
                    return toDto(node);
                });
    }

    public Optional<NodeDto> getNode(UUID nodeId) {
        return findNodeById(nodeId)
                .map(this::toDto);
    }

    public Optional<NodeDto> getNodeForCreator(UUID nodeId, UUID ownerUserId) {
        return findNodeByIdAndOwner(nodeId, ownerUserId).map(this::toDto);
    }

    public List<NodeDto> listByCreator(UUID ownerUserId) {
        return findNodesByOwner(ownerUserId).stream().map(this::toDto).toList();
    }

    public List<NodeDto> listByCreatorAndType(UUID ownerUserId, NodeType type) {
        return findNodesByOwnerAndType(ownerUserId, type).stream().map(this::toDto).toList();
    }

    private void applyPayload(MeshNode node, NodePayload payload) {
        node.nodeType = payload.nodeType();
        node.title = payload.title().trim();
        node.description = payload.description().trim();
        node.tags = payload.tags();
        node.structuredData = payload.structuredData();
        node.country = payload.country();
    }

    String nodeToText(MeshNode node) {
        return EmbeddingTextBuilder.buildText(node);
    }

    NodeDto toDto(MeshNode node) {
        return new NodeDto(
                node.id,
                node.nodeType,
                node.title,
                node.description,
                node.tags,
                node.structuredData,
                node.country,
                node.createdAt,
                node.updatedAt
        );
    }

    MeshNode createEmptyNode() {
        return new MeshNode();
    }

    Optional<MeshNode> findNodeById(UUID nodeId) {
        return MeshNode.findByIdOptional(nodeId);
    }

    Optional<MeshNode> findNodeByIdAndOwner(UUID nodeId, UUID ownerUserId) {
        return MeshNode.findByIdAndOwner(nodeId, ownerUserId);
    }

    List<MeshNode> findNodesByOwner(UUID ownerUserId) {
        return MeshNode.findByOwner(ownerUserId);
    }

    List<MeshNode> findNodesByOwnerAndType(UUID ownerUserId, NodeType type) {
        return MeshNode.findByOwnerAndType(ownerUserId, type);
    }

    float[] generateEmbedding(String text) {
        return embeddingService.generateEmbedding(text);
    }

    void persistNode(MeshNode node) {
        node.persist();
    }

    void logAudit(UUID userId, String action, String toolName) {
        auditService.log(userId, action, toolName);
    }
}
