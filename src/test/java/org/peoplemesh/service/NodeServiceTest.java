package org.peoplemesh.service;

import org.junit.jupiter.api.Test;
import org.peoplemesh.domain.dto.NodeListItemDto;
import org.peoplemesh.domain.dto.NodeDto;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.model.MeshNode;
import org.peoplemesh.repository.NodeRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NodeServiceTest {

    private final NodeService nodeService = new NodeService();

    @Test
    void toDto_mapsKeyFields() {
        MeshNode node = new MeshNode();
        node.id = UUID.randomUUID();
        node.nodeType = NodeType.PROJECT;
        node.title = "Project X";
        node.description = "Description";
        node.tags = java.util.List.of("tag1");
        node.country = "US";

        NodeDto dto = nodeService.toDto(node, true, false);

        assertEquals(node.id, dto.id());
        assertEquals(NodeType.PROJECT, dto.nodeType());
        assertEquals("Project X", dto.title());
        assertEquals("Description", dto.description());
        assertEquals(java.util.List.of("tag1"), dto.tags());
        assertEquals("US", dto.country());
        assertNull(dto.embedding());
    }

    @Test
    void toListItemDto_includesStructuredDataAndEmbeddingWhenRequested() {
        MeshNode node = new MeshNode();
        node.id = UUID.randomUUID();
        node.nodeType = NodeType.USER;
        node.title = "User";
        node.description = "Desc";
        node.structuredData = java.util.Map.of("k", "v");
        node.embedding = new float[]{0.5f};
        node.searchable = true;

        NodeListItemDto dto = nodeService.toListItemDto(node, true, true);

        assertEquals(node.id, dto.id());
        assertEquals(node.structuredData, dto.structuredData());
        assertEquals(1, dto.embedding().length);
        assertTrue(dto.searchable());
    }

    @Test
    void getNode_withEmbeddingFlagFalse_omitsEmbedding() {
        NodeRepository repository = mock(NodeRepository.class);
        nodeService.nodeRepository = repository;
        UUID id = UUID.randomUUID();
        MeshNode node = new MeshNode();
        node.id = id;
        node.nodeType = NodeType.USER;
        node.title = "Title";
        node.description = "Desc";
        node.embedding = new float[]{1.0f};
        when(repository.findById(id)).thenReturn(Optional.of(node));

        Optional<NodeDto> result = nodeService.getNode(id, false);

        assertTrue(result.isPresent());
        assertNull(result.get().embedding());
    }

    @Test
    void getNode_defaultOverload_returnsDtoWhenPresent() {
        NodeRepository repository = mock(NodeRepository.class);
        nodeService.nodeRepository = repository;
        UUID id = UUID.randomUUID();
        MeshNode node = new MeshNode();
        node.id = id;
        node.nodeType = NodeType.PROJECT;
        node.title = "Project";
        node.description = "Desc";
        when(repository.findById(id)).thenReturn(Optional.of(node));

        Optional<NodeDto> result = nodeService.getNode(id);

        assertTrue(result.isPresent());
        assertEquals(NodeType.PROJECT, result.get().nodeType());
    }

    @Test
    void getNode_returnsEmptyWhenMissing() {
        NodeRepository repository = mock(NodeRepository.class);
        nodeService.nodeRepository = repository;
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        Optional<NodeDto> result = nodeService.getNode(id, true);

        assertTrue(result.isEmpty());
    }

    @Test
    void listNodes_mapsRepositoryResults() {
        NodeRepository repository = mock(NodeRepository.class);
        nodeService.nodeRepository = repository;
        MeshNode one = new MeshNode();
        one.id = UUID.randomUUID();
        one.nodeType = NodeType.USER;
        one.title = "One";
        one.description = "First";
        one.searchable = true;
        MeshNode two = new MeshNode();
        two.id = UUID.randomUUID();
        two.nodeType = NodeType.PROJECT;
        two.title = "Two";
        two.description = "Second";
        two.searchable = false;
        when(repository.listNodes(NodeType.USER, true, 0, 10)).thenReturn(List.of(one, two));

        List<NodeListItemDto> result = nodeService.listNodes(NodeType.USER, true, 0, 10, false, false);

        assertEquals(2, result.size());
        assertEquals("One", result.get(0).title());
        assertFalse(result.get(1).searchable());
        assertNull(result.get(0).embedding());
    }
}
