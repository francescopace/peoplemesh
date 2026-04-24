package org.peoplemesh.service;

import org.junit.jupiter.api.Test;
import org.peoplemesh.domain.dto.NodeDto;
import org.peoplemesh.domain.enums.NodeType;
import org.peoplemesh.domain.model.MeshNode;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
}
