package org.peoplemesh.repository;

import jakarta.enterprise.context.ApplicationScoped;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.model.MeshNode;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class NodeRepository {

    public Optional<MeshNode> findById(UUID nodeId) {
        return MeshNode.findByIdOptional(nodeId);
    }

    public Optional<MeshNode> findByIdAndOwner(UUID nodeId, UUID ownerUserId) {
        return MeshNode.findByIdAndOwner(nodeId, ownerUserId);
    }

    public List<MeshNode> findByOwner(UUID ownerUserId) {
        return MeshNode.findByOwner(ownerUserId);
    }

    public List<MeshNode> findByOwnerAndType(UUID ownerUserId, NodeType type) {
        return MeshNode.findByOwnerAndType(ownerUserId, type);
    }

    public Optional<MeshNode> findPublishedUserNode(UUID nodeId) {
        return MeshNode.findPublishedUserNode(nodeId);
    }

    public Optional<MeshNode> findUserByExternalId(String externalId) {
        return MeshNode.findUserByExternalId(externalId);
    }

    public void persist(MeshNode node) {
        node.persist();
    }
}
