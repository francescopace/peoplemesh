package org.peoplemesh.service;

import org.peoplemesh.domain.dto.NodeDto;
import org.peoplemesh.domain.dto.NodeListItemDto;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.model.MeshNode;
import org.peoplemesh.repository.NodeRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class NodeService {

    @Inject
    NodeRepository nodeRepository;

    public Optional<NodeDto> getNode(UUID nodeId) {
        return findNodeById(nodeId)
                .map(node -> toDto(node, true, false));
    }

    public Optional<NodeDto> getNode(UUID nodeId, boolean includeEmbedding) {
        return findNodeById(nodeId)
                .map(node -> toDto(node, true, includeEmbedding));
    }

    public List<NodeListItemDto> listNodes(NodeType nodeType, Boolean searchable, int page, int size,
                                           boolean includeStructuredData, boolean includeEmbedding) {
        return nodeRepository.listNodes(nodeType, searchable, page, size).stream()
                .map(node -> toListItemDto(node, includeStructuredData, includeEmbedding))
                .toList();
    }

    NodeDto toDto(MeshNode node, boolean includeStructuredData, boolean includeEmbedding) {
        return new NodeDto(
                node.id,
                node.nodeType,
                node.title,
                node.description,
                node.tags,
                includeStructuredData ? node.structuredData : null,
                node.country,
                node.createdAt,
                node.updatedAt,
                includeEmbedding ? node.embedding : null
        );
    }

    NodeListItemDto toListItemDto(MeshNode node, boolean includeStructuredData, boolean includeEmbedding) {
        return new NodeListItemDto(
                node.id,
                node.nodeType,
                node.title,
                node.description,
                node.tags,
                includeStructuredData ? node.structuredData : null,
                node.country,
                node.searchable,
                node.createdAt,
                node.updatedAt,
                includeEmbedding ? node.embedding : null
        );
    }

    Optional<MeshNode> findNodeById(UUID nodeId) {
        return nodeRepository.findById(nodeId);
    }
}
