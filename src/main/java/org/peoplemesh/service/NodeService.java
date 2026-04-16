package org.peoplemesh.service;

import org.peoplemesh.domain.dto.NodeDto;
import org.peoplemesh.domain.dto.NodePayload;
import org.peoplemesh.domain.dto.SkillAssessmentDto;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.exception.ForbiddenBusinessException;
import org.peoplemesh.domain.exception.NotFoundBusinessException;
import org.peoplemesh.domain.exception.ValidationBusinessException;
import org.peoplemesh.domain.model.MeshNode;
import org.peoplemesh.repository.NodeRepository;
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

    @Inject
    NodeRepository nodeRepository;

    @Inject
    EntitlementService entitlementService;

    @Inject
    NodeAccessPolicyService nodeAccessPolicyService;

    @Inject
    SkillAssessmentService skillAssessmentService;

    @Transactional
    public NodeDto createNode(UUID ownerUserId, NodePayload payload) {
        enforceNodeCreationEntitlement(ownerUserId, payload.nodeType());
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
                    enforceNodeCreationEntitlement(ownerUserId, payload.nodeType());
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

    public List<NodeDto> listByCreatorFiltered(UUID ownerUserId, String type) {
        if (type == null || type.isBlank()) {
            return listByCreator(ownerUserId);
        }
        NodeType parsedType;
        try {
            parsedType = NodeType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationBusinessException("Invalid node type: " + type);
        }
        return listByCreatorAndType(ownerUserId, parsedType);
    }

    public List<SkillAssessmentDto> getNodeSkillsForUser(UUID requesterUserId, UUID nodeId, UUID catalogId) {
        MeshNode node = findNodeById(nodeId).orElseThrow(() -> new NotFoundBusinessException("Node not found"));
        if (!nodeAccessPolicyService.canReadNode(requesterUserId, node)) {
            throw new ForbiddenBusinessException("You do not have access to this node");
        }
        return skillAssessmentService.listAssessments(node.id, catalogId);
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
        return nodeRepository.findById(nodeId);
    }

    Optional<MeshNode> findNodeByIdAndOwner(UUID nodeId, UUID ownerUserId) {
        return nodeRepository.findByIdAndOwner(nodeId, ownerUserId);
    }

    List<MeshNode> findNodesByOwner(UUID ownerUserId) {
        return nodeRepository.findByOwner(ownerUserId);
    }

    List<MeshNode> findNodesByOwnerAndType(UUID ownerUserId, NodeType type) {
        return nodeRepository.findByOwnerAndType(ownerUserId, type);
    }

    float[] generateEmbedding(String text) {
        return embeddingService.generateEmbedding(text);
    }

    void persistNode(MeshNode node) {
        nodeRepository.persist(node);
    }

    void logAudit(UUID userId, String action, String toolName) {
        auditService.log(userId, action, toolName);
    }

    private void enforceNodeCreationEntitlement(UUID ownerUserId, NodeType nodeType) {
        if (nodeType == NodeType.JOB && !entitlementService.canCreateJob(ownerUserId)) {
            throw new ForbiddenBusinessException("Insufficient entitlement to create job nodes");
        }
    }
}
